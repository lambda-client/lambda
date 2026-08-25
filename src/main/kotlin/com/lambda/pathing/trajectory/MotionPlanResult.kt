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
    ) : MotionPlanResult

    data class UnsupportedRoute(val movements: Set<MovementId>) : MotionPlanResult

    data class NoSafeStop(
        val attemptCount: Int,
        val nearest: PlanAttempt?,
        val remainingStart: Stance? = null,
        val remainingGoal: Stance? = null,
        val blockedProgress: Int? = null,
    ) : MotionPlanResult

    data class UnstableReplay(val reason: String) : MotionPlanResult
}
