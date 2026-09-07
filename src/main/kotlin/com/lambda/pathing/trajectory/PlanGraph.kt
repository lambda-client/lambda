package com.lambda.pathing.trajectory

import com.lambda.pathing.movement.MotionConstraints
import com.lambda.pathing.prediction.simulation.MovementSimulationState

/**
 * A body between two segments, and whether a branch may rejoin there. Junctions are the
 * only places a plan can be cut: a decision either runs or it does not.
 */
class PlanJunction(
    val index: Int,
    val frame: Int,
    val state: MovementSimulationState,
    val settled: Boolean,
)

/**
 * A plan as a graph over its junctions: the spine is the committed segment chain to the
 * goal, an [Alternate] departs one junction and rejoins a later one, and [bestRoute] is
 * the cheapest route through both (a linear DP; edges run forward). Alternates spanning
 * more than the re-certification depth should expect refusal; see docs/decisions/improver.md.
 */
class PlanGraph private constructor(
    val junctions: List<PlanJunction>,
    val spine: List<PlanSegment>,
    val alternates: List<Alternate>,
) {
    /** A candidate replacement for the spine between two junctions. */
    class Alternate(
        val from: Int,
        val to: Int,
        val segments: List<PlanSegment>,
    ) {
        val frames: Int get() = segments.sumOf { it.frameCount }
    }

    val frames: Int get() = spine.sumOf { it.frameCount }

    /** Frames the spine spends between two junctions. */
    fun spineFrames(from: Int, to: Int): Int =
        spine.subList(from, to).sumOf { it.frameCount }

    /** The cheapest route from start to goal over spine edges and alternates; one forward sweep. */
    fun bestRoute(): List<PlanSegment> {
        val last = junctions.lastIndex
        val cost = IntArray(last + 1) { Int.MAX_VALUE }
        val via = arrayOfNulls<Alternate>(last + 1)
        val from = IntArray(last + 1) { -1 }
        cost[0] = 0
        for (at in 0 until last) {
            if (cost[at] == Int.MAX_VALUE) continue
            val direct = cost[at] + spine[at].frameCount
            if (direct < cost[at + 1]) {
                cost[at + 1] = direct
                from[at + 1] = at
                via[at + 1] = null
            }
            alternates.forEach { alternate ->
                if (alternate.from != at) return@forEach
                val through = cost[at] + alternate.frames
                if (through < cost[alternate.to]) {
                    cost[alternate.to] = through
                    from[alternate.to] = at
                    via[alternate.to] = alternate
                }
            }
        }

        val route = ArrayList<PlanSegment>()
        var at = last
        while (at > 0) {
            val previous = from[at]
            if (previous < 0) return spine
            val alternate = via[at]
            if (alternate == null) route += spine[previous] else route.addAll(alternate.segments)
            at = previous
        }
        route.reverse()
        return route
    }

    /** This graph with one more alternate offered; the original is unchanged. */
    fun with(alternate: Alternate): PlanGraph =
        PlanGraph(junctions, spine, alternates + alternate)

    /**
     * Settled-to-settled spine spans, dearest first by frames per block of straight-line
     * displacement (a proxy: a climb legitimately costs more per block). [maxSpan] keeps
     * candidates inside the re-certification depth.
     */
    fun improvementTargets(maxSpan: Int = DEFAULT_MAX_SPAN): List<Span> {
        val targets = ArrayList<Span>()
        for (start in junctions.indices) {
            if (!junctions[start].settled) continue
            for (end in start + 1..minOf(start + maxSpan, junctions.lastIndex)) {
                if (!junctions[end].settled) continue
                val frames = spineFrames(start, end)
                if (frames <= 0) continue
                val displacement = junctions[start].state.position
                    .distanceTo(junctions[end].state.position)
                if (displacement < MIN_SPAN_BLOCKS) continue
                targets += Span(start, end, frames, frames / displacement)
            }
        }
        targets.sortByDescending { it.framesPerBlock }
        return targets
    }

    class Span(val from: Int, val to: Int, val frames: Int, val framesPerBlock: Double)

    companion object {
        /** Segments a shortcut may span: the re-certification depth. See docs/decisions/improver.md. */
        const val DEFAULT_MAX_SPAN = 8

        private const val MIN_SPAN_BLOCKS = 1.0

        fun of(plan: TrajectoryPlan, constraints: MotionConstraints = MotionConstraints()): PlanGraph? =
            of(plan.segments, plan.initialState, constraints)

        /** For a spine assembled by hand -- a spliced plan, before it is re-certified. */
        fun of(
            spine: List<PlanSegment>,
            initialState: MovementSimulationState,
            constraints: MotionConstraints = MotionConstraints(),
        ): PlanGraph? {
            if (spine.isEmpty()) return null

            val junctions = ArrayList<PlanJunction>(spine.size + 1)
            junctions += PlanJunction(
                index = 0,
                frame = 0,
                state = initialState,
                // The body departs from wherever it is; the start is always a legal cut.
                settled = true,
            )
            spine.forEachIndexed { index, segment ->
                junctions += PlanJunction(
                    index = index + 1,
                    frame = segment.endFrame,
                    state = segment.exit,
                    settled = segment.settledExit(constraints.stoppedSpeed),
                )
            }
            return PlanGraph(junctions, spine, emptyList())
        }
    }
}
