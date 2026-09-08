package pathing

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.actions.TerminalApproach
import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.search.PlanGraph
import com.lambda.pathing.search.PlanSegment
import net.minecraft.util.math.Vec3d
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** Cheap graph-only tests: distinguish exact segment order from merely shorter totals. */
class PlanGraphRoutingTest {
    @Test
    fun `a spine without alternatives needs no route materialization`() {
        val graph = graphFixture(8)
        assertSame(graph.spine, graph.bestRoute())
    }

    @Test
    fun `multi segment shortcut preserves execution order and terminal position`() {
        val graph = graphFixture(6)
        val move = graphSegment(2).let {
            PlanSegment.Move(
                TrajectoryDecision.Walk(false, null, 1, false), emptyList(),
                it.entry, it.exit, it.inputs, it.startFrame,
            )
        }
        val shortcut = listOf(move, graphSegment(3))
        val routed = graph.with(PlanGraph.Alternate(1, 6, shortcut)).bestRoute()
        assertEquals(listOf(graph.spine.first()) + shortcut, routed)
    }

    @Test
    fun `multiple disjoint shortcuts preserve their internal order`() {
        val graph = graphFixture(8)
        val first = listOf(graphSegment(1), graphSegment(2))
        val second = listOf(graphSegment(3), graphSegment(4))
        val routed = graph.with(PlanGraph.Alternate(0, 3, first))
            .with(PlanGraph.Alternate(4, 8, second)).bestRoute()
        assertEquals(first + graph.spine[3] + second, routed)
    }

    @Test
    fun `equal cost alternatives keep the first offered route`() {
        val graph = graphFixture(4)
        val first = listOf(graphSegment(3), graphSegment(2))
        val second = listOf(graphSegment(1), graphSegment(4))
        assertEquals(first, graph.with(PlanGraph.Alternate(0, 4, first))
            .with(PlanGraph.Alternate(0, 4, second)).bestRoute())
    }

    @Test
    fun `random forward graphs match a chronological reference solver`() {
        val random = Random(8731)
        repeat(200) {
            var graph = graphFixture(random.nextInt(2, 25))
            repeat(50) {
                val from = random.nextInt(graph.spine.size)
                val to = random.nextInt(from + 1, graph.junctions.size)
                val segments = List(random.nextInt(1, 5)) { graphSegment(random.nextInt(1, 15)) }
                graph = graph.with(PlanGraph.Alternate(from, to, segments))
            }
            val routes = arrayOfNulls<List<PlanSegment>>(graph.junctions.size)
            routes[0] = emptyList()
            fun offer(from: Int, to: Int, segments: List<PlanSegment>) {
                val candidate = routes[from]!! + segments
                if (routes[to] == null || candidate.sumOf { it.frameCount } < routes[to]!!.sumOf { it.frameCount }) {
                    routes[to] = candidate
                }
            }
            for (at in graph.spine.indices) {
                offer(at, at + 1, listOf(graph.spine[at]))
                graph.alternates.filter { it.from == at }.forEach { offer(at, it.to, it.segments) }
            }
            assertEquals(routes.last(), graph.bestRoute(), "random graph $it")
        }
    }

    @Test
    fun `span costs equal sums for every range including empty spans`() {
        val graph = graphFixture(16)
        for (from in graph.junctions.indices) for (to in from..graph.spine.size) {
            assertEquals(graph.spine.subList(from, to).sumOf { it.frameCount }, graph.spineFrames(from, to))
        }
        assertEquals(graph.spine.sumOf { it.frameCount }, graph.frames)
    }

    @Test
    fun `departure ranking matches stable span sort on random geometry and cut predicates`() {
        val random = Random(548921)
        repeat(100) { case ->
            var state = graphSegment(1).entry
            var frame = 0
            val spine = List(random.nextInt(2, 24)) {
                val segment = graphSegment(random.nextInt(0, 20), frame)
                val exit = state.copy(position = Vec3d(
                    random.nextInt(-5, 6).toDouble(), 100.0, random.nextInt(-5, 6).toDouble(),
                ))
                PlanSegment.Terminal(TerminalApproach(false, 1, 0.0, null), state, exit,
                    segment.inputs, frame).also {
                    state = exit
                    frame += segment.frameCount
                }
            }
            val graph = PlanGraph.of(spine, spine.first().entry,
                rejoinable = { it.position.x.toInt() % 3 != 0 })!!
            for (maxSpan in listOf(0, 1, 3, 8, 32)) {
                val reference = graph.improvementTargets(maxSpan).map { it.from }.distinct()
                for (before in 0..graph.junctions.size) {
                    assertEquals(reference.filter { it < before }, graph.improvementDepartures(before, maxSpan),
                        "case=$case maxSpan=$maxSpan before=$before")
                }
            }
        }
    }

    @Test
    fun `equal span scores preserve earliest departure first`() {
        val graph = graphFixture(32)
        assertEquals((0 until 32).toList(), graph.improvementDepartures())
        assertEquals((0 until 8).toList(), graph.improvementDepartures(beforeJunction = 8))
    }

    @Test
    fun `costs use input counts rather than pre splice offsets`() {
        val spine = listOf(graphSegment(3, 20), graphSegment(7, 80), graphSegment(2, 150))
        val graph = PlanGraph.of(spine, spine.first().entry)!!
        val branch = graph.with(PlanGraph.Alternate(0, 2, listOf(graphSegment(1))))
        assertEquals(12, graph.frames)
        assertEquals(9, graph.spineFrames(1, 3))
        assertEquals(graph.frames, branch.frames)
        assertEquals(graph.spineFrames(1, 3), branch.spineFrames(1, 3))
    }
}

internal fun graphFixture(size: Int): PlanGraph {
    var frame = 0
    val spine = List(size) {
        graphSegment(10 + it % 3, frame).also { frame += it.frameCount }
    }
    return PlanGraph.of(spine, spine.first().entry)!!
}

internal fun graphSegment(frames: Int, start: Int = 0): PlanSegment = PlanSegment.Terminal(
    approach = TerminalApproach(false, 1, 0.0, null),
    entry = graphState(start),
    exit = graphState(start + frames),
    inputs = List(frames) { MovementSimulationInput() },
    startFrame = start,
)

private fun graphState(frame: Int) = MovementSimulationState.synthetic(
    profile = ProbeScenarios.PROFILE,
    position = Vec3d(frame.toDouble(), 100.0, 0.5),
    rotation = Rotation(0.0, 0.0),
    velocity = Vec3d(0.1, 0.0, 0.0),
    onGround = true,
)
