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
    /**
     * Largest per-frame yaw step any simulated controller may plan. Production fills
     * this from the requester's rotation-config turn speed (sampled once at capture),
     * so a tape never asks for a turn the rotation manager was not set up to deliver.
     */
    val maxYawDegreesPerFrame: Double = 30.0,
    val goalRadius: Double = 0.20,
    val stoppedSpeed: Double = 0.012,
    val stableStopFrames: Int = 3,
    /** Vanilla takes fall damage above three blocks. Safety is a hard gate. */
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

/** How the tape brakes onto the goal, and the gait it arrives in. */
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
    /** Null when this attempt reached a stable grounded stop at the goal. */
    val diagnostic: TrajectoryDiagnostic?,
    /** Route node reached when [diagnostic] occurred; used for exact edge attribution. */
    val blockedProgress: Int = 0,
)

sealed interface MotionPlanResult {
    data class Success(
        val sourceRoute: CoarseRoutePlan,
        val tape: InputTape,
        val rollout: TrajectoryRollout,
        val parameters: TerminalApproach,
        /** Union of coarse template reads and every snapshot voxel read by simulation. */
        val dependencies: Set<VoxelPos>,
        val attempts: List<PlanAttempt>,
        /** Piecewise control programs concatenated into this one replayable tape. */
        val controlSegments: Int = 1,
        /** Frame boundaries between worker-local controllers; execution ignores them. */
        val spliceFrames: List<Int> = emptyList(),
        /** Accumulated launch runway in frames across the certified tape. */
        val launchMarginFrames: Int = 0,
    ) : MotionPlanResult

    data class UnsupportedRoute(val edgeKinds: Set<CoarseMoveKind>) : MotionPlanResult

    data class NoSafeStop(
        val attempts: List<PlanAttempt>,
        /** Coarse suffix on which the search finally ran out of options. */
        val remainingStart: Stance? = null,
        val remainingGoal: Stance? = null,
        /** Route-node index of the diagnostic in the local suffix. */
        val blockedProgress: Int? = null,
        /**
         * The first coarse edge of the suffix this exact entry state could not get past.
         * Null when no edge remained to blame.
         */
        val deadEdge: CoarseEdge? = null,
    ) : MotionPlanResult {
        /** The failure the search got closest to solving. */
        val nearest: PlanAttempt? get() = attempts.minByOrNull { it.finalGoalError }
    }

    /**
     * The accepted tape did not reproduce on a fresh replay. This must be impossible
     * -- same snapshot, same profile, same inputs -- so it is a typed result rather
     * than a crash in a worker thread.
     */
    data class UnstableReplay(val reason: String) : MotionPlanResult
}
