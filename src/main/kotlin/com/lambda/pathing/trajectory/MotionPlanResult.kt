package com.lambda.pathing.trajectory

import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.core.Stance
import com.lambda.pathing.movement.InputTape
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.movement.TerminalApproach
import com.lambda.pathing.core.VoxelPos

data class PlanAttempt(
    val parameters: TerminalApproach,
    val simulatedFrames: Int,
    val finalGoalError: Double,
    val finalHorizontalSpeed: Double,
    val diagnostic: TrajectoryDiagnostic?,
    val blockedProgress: Int = 0,
)

internal class AttemptAccumulator {
    var count: Int = 0
        private set
    var nearest: PlanAttempt? = null
        private set

    fun record(attempt: PlanAttempt) {
        count++
        if (nearest == null || attempt.finalGoalError < nearest!!.finalGoalError) nearest = attempt
    }
}

/**
 * What the search had spent, unlocked and reached when it stopped.
 *
 * Recorded on the way out whether or not a tape was found, because the question worth
 * answering is comparative: a session that dead-ends mid-walk was measured burning 64,001
 * attempts on a stance that a session started fresh from the same body cleared in 400.
 * Two searches, one goal, a 160-fold difference -- so the interesting quantity is not the
 * failure on its own but which of these fields differ between the two.
 */
data class SearchExhaustion(
    val exit: String,
    val expansions: Int,
    val windowExpansions: Int,
    val windowBudget: Int,
    val corridorLevel: Int,
    val temperature: Double,
    val openAnchors: Int,
    val parkedAnchors: Int,
    val blockedAttempts: Int,
    val spentAnchors: Int,
    val deepestAnchorFrame: Int,
    val unreachableAdmissions: Int,
    val tapeRestarts: Int,
    val routeNodes: Int,
    val deepestRouteIndex: Int,
    val committed: Boolean,
    val rootFrame: Int,
    val publishedFrame: Int,
    val horizonEnd: Int,
    val anchorsAdmitted: Int,
    val beamDominated: Int,
    val beamEvicted: Int,
    val beamCapped: Int,
    val beamBuckets: Int,
    val beamLargestBucket: Int,
) {
    override fun toString(): String = buildString {
        append("exit=").append(exit)
        append(" expansions=").append(expansions)
        append(" window=").append(windowExpansions).append('/').append(windowBudget)
        append(" corridor=").append(corridorLevel)
        append(" temp=%.2f".format(temperature))
        append(" open=").append(openAnchors)
        append(" parked=").append(parkedAnchors)
        append(" blocked=").append(blockedAttempts)
        append(" spent=").append(spentAnchors)
        append(" deepestFrame=").append(deepestAnchorFrame)
        append(" unreachable=").append(unreachableAdmissions)
        append(" restarts=").append(tapeRestarts)
        append(" route=").append(deepestRouteIndex).append('/').append(routeNodes)
        append(" committed=").append(committed)
        append(" root=").append(rootFrame)
        append(" published=").append(publishedFrame)
        append(" horizonEnd=").append(horizonEnd)
        append(" admitted=").append(anchorsAdmitted)
        append(" merged=").append(beamDominated + beamEvicted + beamCapped)
        append(" buckets=").append(beamBuckets)
        append(" maxBucket=").append(beamLargestBucket)
    }
}

sealed interface MotionPlanResult {
    data object Cancelled : MotionPlanResult

    data class Success(
        val sourceRoute: CoarseRoutePlan,
        val tape: InputTape,
        val rollout: TrajectoryRollout,
        val parameters: TerminalApproach,

        val safeAnchorStance: Stance,
        val safeAnchorFrame: Int,
        val remainingGuideTicks: Double,

        val frameDependencies: List<Set<VoxelPos>>,

        val dependencies: Set<VoxelPos>,
        val attemptCount: Int,
        val controlSegments: Int = 1,
        val spliceFrames: List<Int> = emptyList(),
        val launchMarginFrames: Int = 0,

        /**
         * Where the tape's frames went, against what the coarse route says they had to.
         *
         * [CoarseRoutePlan.lowerBoundTicks] is admissible: no body can cross that route in
         * fewer ticks. It is therefore the denominator the trajectory layer has never been
         * measured against -- tape length on its own says a walk is 209 frames without
         * saying whether that is excellent or twice what it should be.
         */
        val segments: List<TapeSegment> = emptyList(),
    ) : MotionPlanResult {
        val lowerBoundTicks: Double get() = sourceRoute.lowerBoundTicks

        /** Frames actually spent per frame the route could not have avoided. */
        val excessRatio: Double
            get() = if (lowerBoundTicks > 0.0) rollout.frames.size / lowerBoundTicks else Double.NaN

        fun profile(): String = buildString {
            append("frames=").append(rollout.frames.size)
            append(" bound=%.0f".format(lowerBoundTicks))
            append(" ratio=%.2fx".format(excessRatio))
            segments.groupBy { it.movement }
                .mapValues { (_, v) -> v.sumOf { it.frames } to v.size }
                .entries.sortedByDescending { it.value.first }
                .forEach { (movement, cost) ->
                    append(' ').append(movement).append('=').append(cost.first)
                    append('/').append(cost.second)
                }
        }
    }

    data class UnsupportedRoute(val movements: Set<MovementId>) : MotionPlanResult

    data class NoSafeStop(
        val attemptCount: Int,
        val nearest: PlanAttempt?,
        val remainingStart: Stance? = null,
        val remainingGoal: Stance? = null,
        val blockedProgress: Int? = null,
        val exhaustion: SearchExhaustion? = null,
    ) : MotionPlanResult

    data class UnstableReplay(val reason: String) : MotionPlanResult
}
