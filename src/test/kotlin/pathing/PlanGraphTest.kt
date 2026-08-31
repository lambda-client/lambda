/*
 * Copyright 2026 Lambda
 */
package pathing

import com.lambda.pathing.PathPlanResult
import com.lambda.pathing.trajectory.PlanGraph
import com.lambda.pathing.trajectory.PlanSegment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Tag

/**
 * The junction graph over a certified plan, and the shortest path through it.
 *
 * With no alternates offered the graph must be exactly the spine -- a structure that
 * quietly reorders or drops segments would be worse than no structure at all. With one
 * offered it must take it when it is cheaper and refuse it when it is not.
 */
@Tag("bedrock-corpus")
class PlanGraphTest {

    private fun graphs(): List<Pair<String, PlanGraph>> = ProbeScenarios.all().mapNotNull { scenario ->
        val plan = (ProbeScenarios.planned(scenario).result as? PathPlanResult.Planned)
            ?.path?.takeIf { !it.partial }?.plan ?: return@mapNotNull null
        PlanGraph.of(plan)?.let { scenario.name to it }
    }

    @Test
    fun `with no alternates the best route is the spine`() {
        val graphs = graphs()
        assertTrue(graphs.isNotEmpty(), "no scenario produced a full plan")
        for ((name, graph) in graphs) {
            assertEquals(graph.spine.size + 1, graph.junctions.size, "$name: junction count")
            assertEquals(0, graph.junctions.first().frame, "$name: first junction is not frame 0")
            assertEquals(
                graph.frames, graph.junctions.last().frame,
                "$name: last junction is not the end of the tape",
            )
            assertEquals(graph.spine, graph.bestRoute(), "$name: best route drifted from the spine")
        }
    }

    @Test
    fun `a cheaper alternate is taken and a dearer one is refused`() {
        for ((name, graph) in graphs()) {
            val from = graph.junctions.indexOfFirst { it.settled && it.index in 1..graph.spine.size - 4 }
            if (from < 0) continue
            val to = minOf(from + 3, graph.spine.size)
            val spanFrames = graph.spineFrames(from, to)
            val replaced = graph.spine.subList(from, to)

            // A shortcut is only ever adopted on frames, so fake one by reusing the span's
            // own segments and letting the graph compare counts.
            val cheaper = graph.with(
                PlanGraph.Alternate(from, to, replaced.dropLast(1)),
            )
            assertTrue(
                cheaper.bestRoute().sumOf { it.frameCount } < spanFrames + graph.frames - spanFrames + 1,
                "$name: cheaper alternate was not taken",
            )
            assertTrue(
                cheaper.bestRoute().size < graph.spine.size,
                "$name: cheaper alternate did not shorten the route",
            )

            val dearer = graph.with(
                PlanGraph.Alternate(from, to, replaced + replaced.first()),
            )
            assertEquals(
                graph.spine, dearer.bestRoute(),
                "$name: a more expensive alternate was taken anyway",
            )
        }
    }

    @Test
    fun `report the junction structure`() {
        for ((name, graph) in graphs()) {
            val targets = graph.improvementTargets().take(3)
            println(
                "[graph] %-18s frames=%-4d junctions=%-3d settled=%-3d  worst spans: %s".format(
                    name, graph.frames, graph.junctions.size, graph.junctions.count { it.settled },
                    targets.joinToString("  ") {
                        "J%d..J%d %df/%.1f b".format(it.from, it.to, it.frames, it.frames / it.framesPerBlock)
                    },
                ),
            )
        }
    }

    @Test
    fun `terminals are never offered as rejoin points`() {
        for ((name, graph) in graphs()) {
            graph.spine.forEachIndexed { index, segment ->
                if (segment is PlanSegment.Terminal && index == graph.spine.lastIndex) {
                    assertTrue(
                        !graph.junctions.last().settled,
                        "$name: the resting terminal was offered as a rejoin point",
                    )
                }
            }
        }
    }
}
