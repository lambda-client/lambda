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

internal class BrakeToStopProgram(private val heldYaw: Double) : ControlProgram {
    override fun input(frame: Int, observed: MovementSimulationState): MovementSimulationInput =
        MovementSimulationInput(
            forward = 0.0,
            strafe = 0.0,
            sprint = false,
            jump = false,
            rotation = Rotation(heldYaw, observed.rotation.pitch),
        )
}
