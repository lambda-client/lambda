package com.lambda.pathing.movement

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.prediction.simulation.MovementSimulationInput
import com.lambda.pathing.prediction.simulation.MovementSimulationState

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
