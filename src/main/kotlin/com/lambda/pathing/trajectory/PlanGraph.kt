package com.lambda.pathing.trajectory

import com.lambda.pathing.movement.MotionConstraints
import com.lambda.pathing.prediction.simulation.MovementSimulationState

/**
 * A junction: a body between two segments, and whether a branch may rejoin there.
 *
 * Junctions are the only places a plan can be cut. They exist because segments are
 * decisions, and a decision either runs or it does not -- there is no meaningful body
 * state halfway through a launch to splice against.
 */
class PlanJunction(
    val index: Int,
    val frame: Int,
    val state: MovementSimulationState,
    val settled: Boolean,
)

/**
 * A plan as a graph over its junctions, so improvement is a shortest path rather than a
 * re-search.
 *
 * The spine is the committed chain of segments to the goal, one edge per junction pair.
 * An [Alternate] is a chain of segments that departs one junction and rejoins a later
 * one, and the plan the body should actually walk is the cheapest route through both --
 * which for a graph this small is a linear DP, not a search.
 *
 * The point of the structure is that it makes the two questions separable. "Do we have a
 * path" is answered by the spine, always, from the moment one exists. "Is it a good path"
 * is answered by shortcuts competing against spine spans, each one bounded, independently
 * verifiable, and cheap to throw away. The old search had to answer both at once with one
 * frontier, which is why it could be a hundred thousand expansions deep and still have
 * nothing to hand the body.
 *
 * Rejoin tolerance is measured, not assumed: re-running a decision chain from a body a
 * quarter of a beam bucket off re-certifies about six segments before it diverges, so an
 * alternate spanning much more than that should expect to be refused.
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

    /**
     * The cheapest route from start to goal over spine edges and alternates.
     *
     * Junctions are topologically ordered by construction -- every edge runs forward --
     * so one sweep in index order settles it.
     */
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
     * Spine spans worth attacking, dearest first.
     *
     * Ranked by frames spent per block of ground covered, against the span's straight-line
     * displacement. It is a proxy -- a climb legitimately costs more per block than a
     * sprint -- but it is self-contained, and it reliably finds the stretches where the
     * body wandered, which is what a shortcut is for. [maxSpan] keeps candidates inside
     * the measured re-certification depth.
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
        /** Segments a shortcut may span, from the measured re-certification depth. */
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
