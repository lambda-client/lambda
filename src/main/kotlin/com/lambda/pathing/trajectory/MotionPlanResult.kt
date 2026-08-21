/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.trajectory

import com.lambda.pathing.coarse.CoarseEdge
import com.lambda.pathing.coarse.CoarseMoveKind
import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.coarse.Stance
import com.lambda.pathing.world.VoxelPos

data class MotionConstraints(
    val maxFrames: Int = 160,
    val maxYawDegreesPerFrame: Double = 30.0,
    val goalRadius: Double = 0.20,
    val stoppedSpeed: Double = 0.012,
    val stableStopFrames: Int = 3,
    val maxSafeFallDistance: Double = 3.0,
    val brakeDistances: List<Double> = listOf(0.25, 0.35, 0.45, 0.55, 0.70, 0.90, 1.15),
    val stepUpJumpLeadDistances: List<Double> = listOf(0.30, 0.55, 0.80, 1.05),
    val sprintModes: List<Boolean> = listOf(true, false),
) {
    init {
        require(maxFrames > 0)
        require(maxYawDegreesPerFrame > 0.0 && maxYawDegreesPerFrame.isFinite())
        require(goalRadius > 0.0 && goalRadius.isFinite())
        require(stoppedSpeed >= 0.0 && stoppedSpeed.isFinite())
        require(stableStopFrames > 0)
        require(maxSafeFallDistance >= 0.0 && maxSafeFallDistance.isFinite())
        require(brakeDistances.isNotEmpty() && brakeDistances.all { it > 0.0 && it.isFinite() })
        require(stepUpJumpLeadDistances.isNotEmpty() && stepUpJumpLeadDistances.all { it > 0.0 && it.isFinite() })
        require(sprintModes.isNotEmpty())
    }
}

data class TerminalApproach(
    val sprint: Boolean,
    val lookAheadNodes: Int,
    val brakeDistance: Double,
    val stepUpJumpLeadDistance: Double?,
)

data class PlanAttempt(
    val parameters: TerminalApproach,
    val simulatedFrames: Int,
    val finalGoalError: Double,
    val finalHorizontalSpeed: Double,
    val diagnostic: TrajectoryDiagnostic?,
    val blockedProgress: Int = 0,
)

/** Bounded production summary; full attempt geometry already lives in the optional debug ring. */
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
        /** Last searched anchor before the certified neutral braking tail. */
        val safeAnchorStance: Stance,
        val safeAnchorFrame: Int,
        val remainingGuideTicks: Double,
        /** Exact world reads made by the final replay, grouped by input frame. */
        val frameDependencies: List<Set<VoxelPos>>,
        /** Union of [frameDependencies]; coarse-guide reads are intentionally excluded. */
        val dependencies: Set<VoxelPos>,
        val attemptCount: Int,
        val controlSegments: Int = 1,
        val spliceFrames: List<Int> = emptyList(),
        val launchMarginFrames: Int = 0,
    ) : MotionPlanResult

    data class UnsupportedRoute(val edgeKinds: Set<CoarseMoveKind>) : MotionPlanResult

    data class NoSafeStop(
        val attemptCount: Int,
        val nearest: PlanAttempt?,
        val remainingStart: Stance? = null,
        val remainingGoal: Stance? = null,
        val blockedProgress: Int? = null,
        val deadEdge: CoarseEdge? = null,
    ) : MotionPlanResult

    data class UnstableReplay(val reason: String) : MotionPlanResult
}
