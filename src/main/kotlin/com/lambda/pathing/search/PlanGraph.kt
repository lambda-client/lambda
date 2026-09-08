package com.lambda.pathing.search

import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.physics.MovementSimulationState

/**
 * A body between two segments, and whether a branch may rejoin there. Junctions are the
 * only places a plan can be cut: a decision either runs or it does not. [rejoinable] is
 * the graph's junction predicate (a grounded body by default -- mid-air there is no state
 * to rejoin, a launch is re-solved, not matched); [settled] -- grounded and still moving
 * -- is the trivially safe case and stays available to callers that want it.
 */
class PlanJunction(
    val index: Int,
    val frame: Int,
    val state: MovementSimulationState,
    val settled: Boolean,
    val rejoinable: Boolean,
)

/**
 * A plan as its junctions: the spine is the committed segment chain to the goal and the
 * junctions between its segments are where a repair or a restart may cut it.
 */
class PlanGraph private constructor(
    val junctions: List<PlanJunction>,
    val spine: List<PlanSegment>,
) {
    val frames: Int get() = junctions.last().frame

    /**
     * Where a plan is cut when the world changes under its tail: the last rejoinable
     * junction strictly before [firstAffectedFrame] that the body has not reached yet
     * ([cursorFrame] plus [minLeadFrames] of in-flight margin). Everything before it is
     * still certified against the world as it is; everything after is re-solved from it.
     * Null when no such cut exists and the walk must stop instead.
     */
    fun repairJunction(firstAffectedFrame: Int, cursorFrame: Int, minLeadFrames: Int): PlanJunction? =
        junctions.lastOrNull { junction ->
            junction.index > 0 && junction.rejoinable &&
                junction.frame < firstAffectedFrame &&
                junction.frame >= cursorFrame + minLeadFrames
        }

    /**
     * Departures for the improver, dearest span first: for each rejoinable junction the best
     * frames-per-block score of any rejoinable span up to [maxSpan] ahead of it (a proxy: a
     * climb legitimately costs more per block). Ties keep junction order.
     */
    fun improvementDepartures(beforeJunction: Int = junctions.lastIndex, maxSpan: Int = DEFAULT_MAX_SPAN): List<Int> {
        require(beforeJunction in 0..junctions.size)
        val scores = DoubleArray(beforeJunction) { Double.NEGATIVE_INFINITY }
        for (start in 0 until beforeJunction) {
            if (!junctions[start].rejoinable) continue
            for (end in start + 1..minOf(start + maxSpan, junctions.lastIndex)) {
                if (!junctions[end].rejoinable) continue
                val frames = junctions[end].frame - junctions[start].frame
                if (frames <= 0) continue
                val displacement = junctions[start].state.position.distanceTo(junctions[end].state.position)
                if (displacement < MIN_SPAN_BLOCKS) continue
                val score = frames / displacement
                if (score > scores[start]) scores[start] = score
            }
        }
        return scores.indices.filter { scores[it] != Double.NEGATIVE_INFINITY }.sortedByDescending { scores[it] }
    }

    companion object {
        /** Segments a shortcut may span: the re-certification depth. See docs/decisions/improver.md. */
        const val DEFAULT_MAX_SPAN = 8

        /** Shorter spans have nothing worth crossing. */
        const val MIN_SPAN_BLOCKS = 1.0

        fun rejoinable(state: MovementSimulationState): Boolean = state.onGround

        fun of(
            plan: TrajectoryPlan,
            constraints: MotionConstraints = MotionConstraints(),
            rejoinable: (MovementSimulationState) -> Boolean = ::rejoinable,
        ): PlanGraph? = of(plan.segments, plan.initialState, constraints, rejoinable)

        /** [rejoinable] is the junction predicate: which segment exits a branch may cut in at. */
        fun of(
            spine: List<PlanSegment>,
            initialState: MovementSimulationState,
            constraints: MotionConstraints = MotionConstraints(),
            rejoinable: (MovementSimulationState) -> Boolean = ::rejoinable,
        ): PlanGraph? {
            if (spine.isEmpty()) return null
            val junctions = ArrayList<PlanJunction>(spine.size + 1)
            // The body departs from wherever it is; the start is always a legal cut.
            junctions += PlanJunction(index = 0, frame = 0, state = initialState, settled = true, rejoinable = true)
            spine.forEachIndexed { index, segment ->
                junctions += PlanJunction(
                    index = index + 1,
                    frame = segment.endFrame,
                    state = segment.exit,
                    settled = segment.settledExit(constraints.stoppedSpeed),
                    rejoinable = rejoinable(segment.exit),
                )
            }
            return PlanGraph(junctions, spine)
        }
    }
}
