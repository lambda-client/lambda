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

internal class HeadingFollowerProgram(
    private val targetYaw: Double,
    private val sprint: Boolean,
    private val maxYawChange: Double,
    private val launch: LaunchTrigger? = null,
    private val keys: MovementKeys = MovementKeys.FORWARD,
    private val airborneKeys: MovementKeys = keys,
) : ControlProgram {
    override fun input(frame: Int, observed: MovementSimulationState): MovementSimulationInput {
        val yawError = Rotation.wrap(targetYaw - observed.rotation.yaw)
        val yawDelta = yawError.coerceIn(-maxYawChange, maxYawChange)
        val held = if (observed.onGround) keys else airborneKeys
        return MovementSimulationInput(
            forward = held.forward,
            strafe = held.strafe,
            sprint = sprint,
            jump = launch?.press(observed) == true,
            rotation = Rotation(observed.rotation.yaw + yawDelta, observed.rotation.pitch),
        )
    }

}
