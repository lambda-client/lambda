package com.lambda.pathing.movement

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
