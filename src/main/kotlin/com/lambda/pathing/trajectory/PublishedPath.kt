package com.lambda.pathing.trajectory

import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.core.Stance
import com.lambda.pathing.movement.TerminalApproach
import com.lambda.pathing.prediction.simulation.PlayerPhysicsProfile

data class PublishedPath(
    val route: CoarseRoutePlan,
    val plan: TrajectoryPlan,
    val profile: PlayerPhysicsProfile,
    val parameters: TerminalApproach,
    val safeAnchorStance: Stance,
    val safeAnchorFrame: Int,
    val remainingGuideTicks: Double,
    val attempts: Int,
    val planMillis: Long,
    val finalGoal: Stance,
    val controlSegments: Int = 1,
    val spliceFrames: List<Int> = emptyList(),
    val launchMarginFrames: Int = 0,
    val partial: Boolean = false,
    val planningGeneration: Long = 0L,
    val publicationSequence: Int = 0,

    /** Same-instant arrival comparison against [comparedRunningSequence]; see MotionPlanResult.Success. */
    val arrivalTicksEstimate: Double = Double.NaN,
    val comparedRunningArrivalTicks: Double = Double.NaN,
    val comparedRunningSequence: Long = -1,

    /** How the tape's frames divide between the movements that produced them. */
    val segments: List<TapeSegment> = emptyList(),
) {
    /**
     * Frames spent per frame the coarse route could not have avoided.
     *
     * [com.lambda.pathing.coarse.CoarseRoutePlan.lowerBoundTicks] is admissible -- no body
     * crosses that route in fewer ticks -- so this is the one honest quality number the
     * planner can produce about itself. 1.0 is optimal for the route it was given; a walk
     * that reads 2.5 is not a slow search, it is a tape twice as long as it needs to be.
     *
     * [TERMINAL_STOP_TICKS] is added because the coarse bound prices *travel* and nothing
     * else. Every walk ends by decelerating, centring inside the goal radius and holding
     * still long enough to certify the stop, and none of that crosses ground the bound
     * knows about. Left out, the same fixed cost reads as +8% on a two-hundred-tick route
     * and +76% on a twenty-tick one -- which is exactly the shape that made the corpus
     * parkour fixtures look 71% over while measuring a body that cleared both gaps in 20
     * frames against a bound of 21.
     */
    val excessRatio: Double
        get() = (route.lowerBoundTicks + TERMINAL_STOP_TICKS).let {
            if (it > 0.0) plan.tape.frameCount / it else Double.NaN
        }

    private companion object {
        /**
         * Frames a walk spends arriving rather than travelling.
         *
         * Measured on the parkour fixture: the body lands on the goal block moving at
         * 0.23 b/t and needs sixteen frames to decelerate, settle within the goal radius
         * and hold still for `stableStopFrames`.
         */
        const val TERMINAL_STOP_TICKS = 16.0

        /** Below this the body is standing, not merely slow. Matches the planner's stop test. */
        const val STANDING_SPEED = 0.012

        /** Shortest still run counted as a stop rather than a slow turn. */
        const val MIN_STANDING_RUN = 4
    }

    /** Frames per movement kind, heaviest first, as `movement=frames/segments`. */
    fun movementProfile(): String = segments
        .groupBy { it.movement }
        .mapValues { (_, parts) -> parts.sumOf { it.frames } to parts.size }
        .entries.sortedByDescending { it.value.first }
        .joinToString(" ") { (movement, cost) -> "$movement=${cost.first}/${cost.second}" }

    /**
     * Frames of the finished tape the body spends standing still away from the goal,
     * as run lengths.
     *
     * A published tape always ends in a certified brake, so an unextended one is safe to
     * replay -- but when the search cannot extend before the body arrives at that brake,
     * the body stops, and re-rooting onto the brake bakes the deceleration and hold into
     * the prefix of every tape that follows. Those frames are therefore a permanent
     * record of the planner failing to keep up, and the number is the one worth watching:
     * a walk that stops is nearly always a walk whose tape is mostly this.
     */
    fun standingRunLengths(): List<Int> {
        val frames = plan.frames
        if (frames.isEmpty()) return emptyList()
        val still = frames.map { it.state.velocity.horizontalLength() <= STANDING_SPEED }
        val runs = ArrayList<Int>()
        var index = 0
        while (index < still.size) {
            if (!still[index]) { index++; continue }
            var end = index
            while (end < still.size && still[end]) end++
            // The run that reaches the last frame is the arrival stop, not a stall.
            if (end - index >= MIN_STANDING_RUN && end < still.size) runs += end - index
            index = end
        }
        return runs
    }

    fun standingFrames(): Int = standingRunLengths().sum()

    fun standingRuns(): Int = standingRunLengths().size

    fun standingPercent(): Int {
        val total = plan.tape.frameCount
        return if (total <= 0) 0 else standingFrames() * 100 / total
    }
}
