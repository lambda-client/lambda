package com.lambda.pathing.search

import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.physics.MovementSimulationState

/**
 * A body between two segments, and whether a branch may rejoin there. Junctions are the
 * only places a plan can be cut: a decision either runs or it does not. [rejoinable] is
 * the graph's junction predicate ([RejoinRule.rejoinable] by default); [settled] -- grounded
 * and still moving -- is the trivially safe case and stays available to callers that want it.
 */
class PlanJunction(
    val index: Int,
    val frame: Int,
    val state: MovementSimulationState,
    val settled: Boolean,
    val rejoinable: Boolean,
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
    private val spineCosts: IntArray,
) {
    /**
     * A candidate replacement for the spine between two junctions. One produced by a moving
     * rejoin runs to the last junction: the rejoined decisions are re-run from a displaced
     * body, so their segments are new even though their decisions are the spine's.
     */
    class Alternate(
        val from: Int,
        val to: Int,
        val segments: List<PlanSegment>,
    ) {
        val frames: Int get() = segments.sumOf { it.frameCount }
    }

    val frames: Int get() = spineCosts.last()

    /** Frames the spine spends between two junctions. */
    fun spineFrames(from: Int, to: Int): Int {
        require(from in 0..to && to <= spine.size) { "Invalid spine span: $from..$to" }
        return spineCosts[to] - spineCosts[from]
    }

    /** The cheapest route from start to goal over spine edges and alternates; one forward sweep. */
    fun bestRoute(): List<PlanSegment> {
        if (alternates.isEmpty()) return spine
        val last = junctions.lastIndex
        // Intrusive adjacency over alternate indices avoids a map/list/boxed-key per
        // junction. Prepending in reverse preserves offer order and equal-cost ties.
        val outgoing = IntArray(last + 1) { -1 }
        val next = IntArray(alternates.size)
        for (index in alternates.indices.reversed()) {
            val departure = alternates[index].from
            next[index] = outgoing[departure]
            outgoing[departure] = index
        }
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
            var index = outgoing[at]
            while (index >= 0) {
                val alternate = alternates[index]
                val through = cost[at] + alternate.frames
                if (through < cost[alternate.to]) {
                    cost[alternate.to] = through
                    from[alternate.to] = at
                    via[alternate.to] = alternate
                }
                index = next[index]
            }
        }

        val route = ArrayList<PlanSegment>()
        var at = last
        while (at > 0) {
            val previous = from[at]
            if (previous < 0) return spine
            val alternate = via[at]
            if (alternate == null) {
                route += spine[previous]
            } else {
                // Backtracking reverses edges AND their segments. The final reversal must
                // restore both, or a shortcut's terminal brake precedes its movements.
                for (index in alternate.segments.indices.reversed()) route += alternate.segments[index]
            }
            at = previous
        }
        route.reverse()
        return route
    }

    /**
     * Where a plan is cut when the world changes under its tail: the last rejoinable
     * junction strictly before [firstAffectedFrame] that the body has not reached yet
     * ([cursorFrame] plus [minLeadFrames] of in-flight margin). Everything before it is
     * still certified against the world as it is; everything after is re-solved as an
     * alternate from it. Null when no such cut exists and the walk must stop instead.
     */
    fun repairJunction(firstAffectedFrame: Int, cursorFrame: Int, minLeadFrames: Int): PlanJunction? =
        junctions.lastOrNull { junction ->
            junction.index > 0 && junction.rejoinable &&
                junction.frame < firstAffectedFrame &&
                junction.frame >= cursorFrame + minLeadFrames
        }

    /** This graph with one more alternate offered; the original is unchanged. */
    fun with(alternate: Alternate): PlanGraph =
        PlanGraph(junctions, spine, alternates + alternate, spineCosts)

    /**
     * Rejoinable-to-rejoinable spine spans, dearest first by frames per block of
     * straight-line displacement (a proxy: a climb legitimately costs more per block).
     * [maxSpan] keeps candidates inside the re-certification depth. The one span ranking;
     * the improver departs from these in this order.
     */
    fun improvementTargets(maxSpan: Int = DEFAULT_MAX_SPAN): List<Span> {
        val targets = ArrayList<Span>()
        forEachImprovementSpan(maxSpan, junctions.size) { from, to, frames, score ->
            targets += Span(from, to, frames, score)
        }
        targets.sortByDescending { it.framesPerBlock }
        return targets
    }

    /**
     * The first occurrence of each departure in [improvementTargets], without creating
     * and sorting every span only to discard repeated departures. Ties retain junction
     * order, exactly as the stable span sort does.
     */
    fun improvementDepartures(
        beforeJunction: Int = junctions.lastIndex,
        maxSpan: Int = DEFAULT_MAX_SPAN,
    ): List<Int> {
        require(beforeJunction in 0..junctions.size)
        val scores = DoubleArray(beforeJunction) { Double.NEGATIVE_INFINITY }
        forEachImprovementSpan(maxSpan, beforeJunction) { from, _, _, score ->
            if (score.compareTo(scores[from]) > 0) scores[from] = score
        }
        return scores.indices.asSequence()
            .filter { scores[it] != Double.NEGATIVE_INFINITY }
            .sortedByDescending { scores[it] }
            .toList()
    }

    /** Shared geometry and scoring; callers choose whether they need spans or departures. */
    private inline fun forEachImprovementSpan(
        maxSpan: Int,
        beforeJunction: Int,
        visit: (Int, Int, Int, Double) -> Unit,
    ) {
        for (start in 0 until beforeJunction) {
            if (!junctions[start].rejoinable) continue
            for (end in start + 1..minOf(start + maxSpan, junctions.lastIndex)) {
                if (!junctions[end].rejoinable) continue
                val frames = spineFrames(start, end)
                if (frames <= 0) continue
                val displacement = junctions[start].state.position
                    .distanceTo(junctions[end].state.position)
                if (displacement < MIN_SPAN_BLOCKS) continue
                visit(start, end, frames, frames / displacement)
            }
        }
    }

    class Span(val from: Int, val to: Int, val frames: Int, val framesPerBlock: Double)

    companion object {
        /** Segments a shortcut may span: the re-certification depth. See docs/decisions/improver.md. */
        const val DEFAULT_MAX_SPAN = 8

        /** Shorter spans have nothing worth crossing. */
        const val MIN_SPAN_BLOCKS = 1.0

        fun of(
            plan: TrajectoryPlan,
            constraints: MotionConstraints = MotionConstraints(),
            rejoinable: (MovementSimulationState) -> Boolean = RejoinRule::rejoinable,
        ): PlanGraph? = of(plan.segments, plan.initialState, constraints, rejoinable)

        /**
         * For a spine assembled by hand -- a spliced plan, before it is re-certified.
         * [rejoinable] is the junction predicate: which segment exits a branch may cut in at.
         */
        fun of(
            spine: List<PlanSegment>,
            initialState: MovementSimulationState,
            constraints: MotionConstraints = MotionConstraints(),
            rejoinable: (MovementSimulationState) -> Boolean = RejoinRule::rejoinable,
        ): PlanGraph? {
            if (spine.isEmpty()) return null

            val junctions = ArrayList<PlanJunction>(spine.size + 1)
            // Frame costs are independent of the segments' original tape offsets after a
            // splice. Share this immutable prefix index across versions of the same spine.
            val spineCosts = IntArray(spine.size + 1)
            junctions += PlanJunction(
                index = 0,
                frame = 0,
                state = initialState,
                // The body departs from wherever it is; the start is always a legal cut.
                settled = true,
                rejoinable = true,
            )
            spine.forEachIndexed { index, segment ->
                spineCosts[index + 1] = spineCosts[index] + segment.frameCount
                junctions += PlanJunction(
                    index = index + 1,
                    frame = segment.endFrame,
                    state = segment.exit,
                    settled = segment.settledExit(constraints.stoppedSpeed),
                    rejoinable = rejoinable(segment.exit),
                )
            }
            return PlanGraph(junctions, spine, emptyList(), spineCosts)
        }
    }
}
