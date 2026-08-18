/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.trajectory

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.util.player.prediction.MovementSimulationInput
import com.lambda.util.player.prediction.MovementSimulationState

/**
 * Drives a fixed world heading instead of chasing a node.
 *
 * Every other controller here is pure pursuit: it re-aims at a stance centre each tick, so
 * whatever it does is a walk between block centres and the trajectory can only ever be the
 * grid path the coarse layer already knew. That is the ceiling the value-field search kept
 * hitting — freedom of *choice* between grid chains, but no freedom of *shape*.
 *
 * A held heading has no such reference. The body commits to a direction and lets its own
 * momentum carry it, so the search can express a line that cuts inside a corner, leaves a
 * pad off-centre to set up the next jump, or crosses a gap at an angle no pair of stance
 * centres describes. The value field prices wherever it ends up and the simulator certifies
 * it, which is the whole point of steering by a field rather than a path.
 */
internal class HeadingFollowerProgram(
    private val targetYaw: Double,
    private val sprint: Boolean,
    private val maxYawChange: Double,
    private val launch: LaunchTrigger? = null,
    /** Release forward until the body is pointing where it is going; a turn, not a brake. */
    private val easeUntilAligned: Boolean = false,
) : ControlProgram {
    override fun input(frame: Int, observed: MovementSimulationState): MovementSimulationInput {
        val yawError = Rotation.wrap(targetYaw - observed.rotation.yaw)
        val yawDelta = yawError.coerceIn(-maxYawChange, maxYawChange)
        val forward = if (easeUntilAligned && kotlin.math.abs(yawError) > EASE_TURN_DEGREES) 0.0 else 1.0
        return MovementSimulationInput(
            forward = forward,
            sprint = sprint,
            jump = launch?.press(observed) == true,
            rotation = Rotation(observed.rotation.yaw + yawDelta, observed.rotation.pitch),
        )
    }

    private companion object {
        /** Matches the pursuit follower's easing threshold, so the two agree on what a turn is. */
        const val EASE_TURN_DEGREES = 50.0
    }
}
