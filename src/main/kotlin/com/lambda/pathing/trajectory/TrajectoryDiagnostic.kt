/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.trajectory

import net.minecraft.util.math.Vec3d

/**
 * Why a rollout failed to certify, in a form the search can *act* on.
 *
 * "It refused" is useless to a solver. Each case here names the frame it happened on
 * and the signed quantity that tells the next candidate which way to move -- that is
 * what turns a blind parameter sweep into failure-directed branching.
 *
 * A `null` diagnostic means the rollout reached a stable grounded stop at the goal.
 */
sealed interface TrajectoryDiagnostic {
    /** The frame the failure was observed on. */
    val frame: Int

    /**
     * The body hit a wall while moving horizontally.
     *
     * Backtrack before [frame] and re-approach: a tighter or wider lookahead, or a
     * different target.
     */
    data class HorizontalCollision(
        override val frame: Int,
        val position: Vec3d,
    ) : TrajectoryDiagnostic

    /**
     * The head struck a ceiling while rising. This kills the whole arc family --
     * more speed will not help, only a different launch tick or yaw will.
     */
    data class HeadBonk(
        override val frame: Int,
        val position: Vec3d,
    ) : TrajectoryDiagnostic

    /**
     * The body fell below the route it was following. Backtrack to the recent
     * grounded launch lattice and try pressing jump.
     */
    data class FellBelowRoute(
        override val frame: Int,
        val depth: Double,
    ) : TrajectoryDiagnostic

    /** Strayed off the coarse corridor -- usually a corner cut into open space. */
    data class LeftCorridor(
        override val frame: Int,
        val deviation: Double,
    ) : TrajectoryDiagnostic

    /**
     * The body landed hard enough to take damage.
     *
     * The coarse layer proposes a `WalkOff` from geometry alone -- it knows the drop
     * is clear, not that it is survivable. This is the trajectory layer doing the job
     * only it can: refusing an edge the graph was optimistic about. Safety is a hard
     * gate; a faster route that hurts is not a better route.
     */
    data class HarmfulFall(
        override val frame: Int,
        val fallDistance: Double,
    ) : TrajectoryDiagnostic

    /**
     * Ran out of frames without a stable stop at the goal. [goalError] is signed by
     * distance, not direction: a large error means the walk never arrived, a small
     * one with speed left means it could not brake in time.
     */
    data class NoStop(
        override val frame: Int,
        val goalError: Double,
        val speed: Double,
    ) : TrajectoryDiagnostic

    /** The snapshot refused the physics here. Stop refining through this region. */
    data class UnsupportedPhysics(
        override val frame: Int,
        val reason: String,
    ) : TrajectoryDiagnostic
}
