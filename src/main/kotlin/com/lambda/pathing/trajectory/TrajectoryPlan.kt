/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.trajectory

import com.lambda.pathing.world.VoxelPos
import com.lambda.util.player.prediction.MovementSimulationState
import com.lambda.util.player.prediction.PlayerPhysicsProfile
import java.util.Collections

@JvmInline
value class TrajectoryPlanId(val value: Long)

data class TrajectorySpliceRef(
    val parentPlanId: TrajectoryPlanId,
    val spliceFrame: Int,
) {
    init {
        require(spliceFrame >= 0)
    }
}

enum class CertifiedTerminal {
    STABLE_GROUNDED_STOP,
}

/** Immutable object consumed by execution; it contains no search/controller state. */
class TrajectoryPlan private constructor(
    val id: TrajectoryPlanId,
    val parent: TrajectorySpliceRef?,
    val snapshotRevision: Long,
    val coarseRouteVersion: Long,
    val physicsProfile: PlayerPhysicsProfile,
    val initialState: MovementSimulationState,
    val dependencies: Set<VoxelPos>,
    val tape: InputTape,
    frames: List<SimulatedTrajectoryFrame>,
    val terminal: CertifiedTerminal,
) {
    val frames: List<SimulatedTrajectoryFrame> = Collections.unmodifiableList(ArrayList(frames))
    val certifiedThrough: Int get() = frames.lastIndex

    init {
        require(frames.size == tape.frameCount) { "Every published input must have one expected frame" }
        require(frames.indices.all { frames[it].index == it }) { "Published frames must be contiguous from zero" }
        require(frames.isNotEmpty()) { "A trajectory plan must certify at least one frame" }
    }

    companion object {
        fun fromWalkingSeed(
            id: TrajectoryPlanId,
            seed: WalkingSeedSearchResult.Success,
            physicsProfile: PlayerPhysicsProfile,
            parent: TrajectorySpliceRef? = null,
        ): TrajectoryPlan = TrajectoryPlan(
            id = id,
            parent = parent,
            snapshotRevision = seed.sourceRoute.snapshotRevision,
            coarseRouteVersion = seed.sourceRoute.routeVersion,
            physicsProfile = physicsProfile,
            initialState = seed.rollout.initialState,
            dependencies = Collections.unmodifiableSet(HashSet(seed.dependencies)),
            tape = InputTape(seed.tape.asList()),
            frames = seed.rollout.frames,
            terminal = CertifiedTerminal.STABLE_GROUNDED_STOP,
        )
    }
}
