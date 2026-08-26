package com.lambda.pathing.coarse

import com.lambda.pathing.core.PathingSection
import com.lambda.pathing.core.Stance
import com.lambda.pathing.movement.CoarseEdge
import com.lambda.pathing.movement.SteeringField
import com.lambda.pathing.world.CoarseVoxelView
import kotlin.math.floor
import kotlin.math.hypot

class CoarseValueField(
    val view: CoarseVoxelView,
    private val moves: SimpleMoveLibrary,
    private val label: (Stance) -> Double,
    val goal: Stance,
) : SteeringField {
    private val guides = it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap<Stance>()
        .apply { defaultReturnValue(Double.NaN) }
    private val edges = HashMap<Stance, List<CoarseEdge>>()

    fun invalidate(sections: Set<com.lambda.pathing.core.PathingSection>) {
        if (sections.isEmpty()) return
        val halo = HashSet<com.lambda.pathing.core.PathingSection>(sections.size * 27)
        for (section in sections) {
            for (dx in -1..1) for (dy in -1..1) for (dz in -1..1) {
                halo += com.lambda.pathing.core.PathingSection(
                    section.x + dx, section.y + dy, section.z + dz,
                )
            }
        }

        guides.keys.removeAll { stance ->
            com.lambda.pathing.core.PathingSection(stance.x shr 4, stance.y shr 4, stance.z shr 4) in halo
        }
        edges.keys.removeAll { stance ->
            com.lambda.pathing.core.PathingSection(stance.x shr 4, stance.y shr 4, stance.z shr 4) in halo
        }
    }

    fun lowerBound(stance: Stance): Double = moves.heuristic(stance, goal)

    fun clearGuideCache() {
        guides.clear()
    }

    fun guide(stance: Stance): Double {
        val cached = guides.getDouble(stance)
        if (!cached.isNaN()) return cached

        val direct = label(stance)
        val computed = if (direct.isFinite()) direct else {
            var best = Double.POSITIVE_INFINITY
            for ((_, _, to, _, lowerBoundTicks) in edgesFrom(stance)) {
                val neighbour = label(to)
                if (neighbour.isFinite()) best = minOf(best, lowerBoundTicks + neighbour)
            }
            best
        }
        guides.put(stance, computed)
        return computed
    }

    fun isMapped(stance: Stance): Boolean = guide(stance).isFinite()

    fun edgesFrom(stance: Stance): List<CoarseEdge> =
        edges.getOrPut(stance) { moves.edgesFrom(view, stance) }

    fun isStance(stance: Stance): Boolean = moves.isStance(view, stance)

    fun steps(
        stance: Stance,
        count: Int,
        marginTicks: Double = Double.MAX_VALUE,
        heading: Pair<Double, Double>? = null,
    ): List<CoarseEdge> {
        val byDestination = HashMap<Stance, CoarseEdge>()
        for (edge in edgesFrom(stance)) {
            val incumbent = byDestination[edge.to]
            if (incumbent == null || edge.lowerBoundTicks < incumbent.lowerBoundTicks) {
                byDestination[edge.to] = edge
            }
        }

        byDestination.values.retainAll { isMapped(it.to) }
        val reference = reference(stance, heading)
        val ranked = byDestination.values.sortedWith(
            compareBy<CoarseEdge> { floor((it.lowerBoundTicks + guide(it.to)) / TIE_TICKS) }
                .thenByDescending { alignment(stance, it.to, reference) }
                .thenBy { it.to.y }.thenBy { it.to.x }.thenBy { it.to.z }
        )
        val best = ranked.minOfOrNull { it.lowerBoundTicks + guide(it.to) } ?: return emptyList()

        return ranked.takeWhile { it.lowerBoundTicks + guide(it.to) <= best + marginTicks }.take(count)
    }

    override fun chain(
        stance: Stance,
        firstStep: Stance?,
        length: Int,
        heading: Pair<Double, Double>?,
    ): List<Stance> {
        require(length > 0) { "A steering chain needs at least one node" }
        val chain = ArrayList<Stance>(length + 1)
        chain += stance
        val visited = HashSet<Stance>()
        visited += stance

        var direction = heading
        firstStep?.let {
            direction = (it.x - stance.x).toDouble() to (it.z - stance.z).toDouble()
            chain += it
            visited += it
        }

        while (chain.size <= length && chain.last() != goal) {
            val from = chain.last()
            val next = descend(from, visited, direction) ?: break
            direction = (next.x - from.x).toDouble() to (next.z - from.z).toDouble()
            chain += next
            visited += next
        }
        return chain
    }

    private fun descend(
        from: Stance,
        visited: Set<Stance>,
        heading: Pair<Double, Double>?,
    ): Stance? {
        val candidates = edgesFrom(from).filter { it.to !in visited && guide(it.to).isFinite() }
        if (candidates.isEmpty()) return null
        val best = candidates.minOf { it.lowerBoundTicks + guide(it.to) }
        val tied = candidates.filter { it.lowerBoundTicks + guide(it.to) <= best + TIE_TICKS }
        val reference = reference(from, heading)
        return tied.minWithOrNull(
            compareByDescending<CoarseEdge> { alignment(from, it.to, reference) }
                .thenBy { it.to.y }.thenBy { it.to.x }.thenBy { it.to.z }
        )?.to
    }

    private fun reference(from: Stance, heading: Pair<Double, Double>?): Pair<Double, Double> {
        if (heading != null && hypot(heading.first, heading.second) > 1e-6) return heading
        return Pair((goal.x - from.x).toDouble(), (goal.z - from.z).toDouble())
    }

    private fun alignment(from: Stance, to: Stance, heading: Pair<Double, Double>?): Double {
        val (hx, hz) = heading ?: return 0.0
        val headingLength = hypot(hx, hz)
        if (headingLength <= 1e-9) return 0.0
        val dx = (to.x - from.x).toDouble()
        val dz = (to.z - from.z).toDouble()
        val stepLength = hypot(dx, dz)
        if (stepLength <= 1e-9) return 0.0
        return (hx * dx + hz * dz) / (headingLength * stepLength)
    }

    fun reachesGoal(chain: List<Stance>): Boolean = chain.lastOrNull() == goal

    private companion object {
        const val TIE_TICKS = 0.5
    }
}
