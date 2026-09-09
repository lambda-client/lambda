package pathing

import com.lambda.pathing.actions.CoarseEdge
import com.lambda.pathing.actions.CoarseEdgeId
import com.lambda.pathing.actions.MotionTemplateId
import com.lambda.pathing.coarse.CoarseValueField
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.Stance
import com.lambda.pathing.world.CoarseVoxel
import com.lambda.pathing.world.CoarseVoxelView
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class CoarseValueFieldSelectionTest {
    @Test
    fun `selection matches full stable sort including ties duplicate destinations and margins`() {
        val random = Random(91827)
        val moves = ProbeScenarios.moveLibrary()
        val from = Stance(0, 1, 0)
        val goal = Stance(8, 1, 3)
        val view = object : CoarseVoxelView {
            override fun voxel(x: Int, y: Int, z: Int) = CoarseVoxel.AIR
        }
        repeat(150) {
            val edges = List(random.nextInt(0, 40)) { index ->
                CoarseEdge(
                    CoarseEdgeId(MotionTemplateId(index), from), from,
                    Stance(random.nextInt(-3, 4), random.nextInt(0, 3), random.nextInt(-3, 4)),
                    MovementId.WALK, random.nextInt(0, 8) * 0.25, emptySet(),
                )
            }
            val labels = edges.associate { edge ->
                edge.to to if (random.nextInt(5) == 0) Double.POSITIVE_INFINITY else random.nextInt(0, 12) * 0.25
            }
            val field = CoarseValueField(view, moves, { labels[it] ?: Double.POSITIVE_INFINITY }, goal,
                edgeProvider = { if (it == from) edges else emptyList() })
            for (heading in listOf(null, 0.0 to 0.0, 1.0 to -2.0, 1e-12 to 0.0, Double.NaN to 1.0)) {
                for (margin in listOf(-0.5, 0.0, 0.2, 0.5, 2.0, Double.MAX_VALUE, Double.NaN)) {
                    for (count in listOf(-1, 0, 1, 2, 8)) {
                        val expected = runCatching { reference(field, from, count, margin, heading) }
                        val actual = runCatching { field.steps(from, count, margin, heading) }
                        assertEquals(expected.exceptionOrNull()?.javaClass, actual.exceptionOrNull()?.javaClass)
                        assertEquals(expected.getOrNull(), actual.getOrNull(), "count=$count margin=$margin heading=$heading")
                    }
                }
            }
        }
    }

    private fun reference(field: CoarseValueField, from: Stance, count: Int, margin: Double, heading: Pair<Double, Double>?): List<CoarseEdge> {
        val unique = HashMap<Stance, CoarseEdge>()
        for (edge in field.edgesFrom(from)) {
            val incumbent = unique[edge.to]
            if (incumbent == null || edge.lowerBoundTicks < incumbent.lowerBoundTicks) unique[edge.to] = edge
        }
        unique.values.retainAll { field.isMapped(it.to) }
        val direction = heading?.takeIf { hypot(it.first, it.second) > 1e-6 }
            ?: ((field.goal.x - from.x).toDouble() to (field.goal.z - from.z).toDouble())
        fun alignment(to: Stance): Double {
            val length = hypot(direction.first, direction.second)
            if (length <= 1e-9) return 0.0
            val dx = (to.x - from.x).toDouble()
            val dz = (to.z - from.z).toDouble()
            val stepLength = hypot(dx, dz)
            if (stepLength <= 1e-9) return 0.0
            return (direction.first * dx + direction.second * dz) / (length * stepLength)
        }
        val ranked = unique.values.sortedWith(
            compareBy<CoarseEdge> { floor((it.lowerBoundTicks + field.guide(it.to)) / 0.5) }
                .thenByDescending { alignment(it.to) }
                .thenBy { it.to.y }.thenBy { it.to.x }.thenBy { it.to.z },
        )
        val best = ranked.minOfOrNull { it.lowerBoundTicks + field.guide(it.to) } ?: return emptyList()
        return ranked.takeWhile { it.lowerBoundTicks + field.guide(it.to) <= best + margin }.take(count)
    }
}
