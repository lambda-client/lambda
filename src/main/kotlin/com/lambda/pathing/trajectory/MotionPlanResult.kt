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
import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.coarse.Stance
import com.lambda.pathing.movement.InputTape
import com.lambda.pathing.movement.MovementId
import com.lambda.pathing.movement.TerminalApproach
import com.lambda.pathing.world.VoxelPos

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

    data class UnsupportedRoute(val movements: Set<MovementId>) : MotionPlanResult

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
