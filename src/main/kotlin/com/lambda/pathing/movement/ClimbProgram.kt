/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.movement

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.util.player.prediction.MovementSimulationInput
import com.lambda.util.player.prediction.MovementSimulationState

/**
 * Holds a ladder and moves along it.
 *
 * Climbing has no gait to regulate. Vanilla clamps a climbing body's rise to the movement
 * input and floors its fall at 0.15, so the only controls that matter are facing the
 * climbable and whether forward is held. Descending is simply releasing: the clamp turns
 * the fall into a controlled slide, and sneaking stops it entirely.
 *
 * The controls are decided by [ClimbMovement] and passed in whole, because none of them can
 * be read off the move. The yaw has to come from which face the climbable is hung on, and
 * the press is held for an ascent and for either step across the column's mouth but released
 * for a descent -- a horizontal collision on the way down re-asserts the rise and sends the
 * body back up the ladder it was trying to leave.
 *
 * The jump key is the second way up, and the only one that works without a wall. Vanilla
 * re-asserts the rise on `horizontalCollision || isJumping`, so a body jumping inside a
 * climbable rises whether or not it is touching anything -- which is what a vine hung under
 * an overhang needs, since there is nothing there to press into and holding forward would
 * only walk the body out of the hitbox it has to stay inside.
 *
 * The two are combined rather than chosen between. Where there is a wall the press does the
 * work from a standing start and the jump sustains it once the feet leave the ground; where
 * there is not, the jump does all of it, and the first one -- taken from the ground, so a
 * real jump -- is what puts the body into the column to begin with.
 */
internal class ClimbProgram(
    private val targetYaw: Double?,
    private val holdForward: Boolean,
    private val holdWhileClimbing: Boolean,
    private val climbWithJump: Boolean,
    private val maxYawChange: Double,
) : ControlProgram {
    override fun input(frame: Int, observed: MovementSimulationState): MovementSimulationInput {
        val yawDelta = targetYaw?.let { target ->
            Rotation.wrap(target - observed.rotation.yaw).coerceIn(-maxYawChange, maxYawChange)
        } ?: 0.0
        return MovementSimulationInput(
            // Pressing into the block is what keeps contact; letting go drops off it.
            forward = if (if (observed.onGround) holdForward else holdWhileClimbing) 1.0 else 0.0,
            sprint = false,
            jump = climbWithJump && (!holdForward || !observed.onGround),
            rotation = Rotation(observed.rotation.yaw + yawDelta, observed.rotation.pitch),
        )
    }
}
