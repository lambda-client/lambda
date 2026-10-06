package com.lambda.pathing.actions.control

import com.lambda.pathing.actions.ControlProgram

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState

internal class BrakeToStopProgram(private val heldYaw: Double) : ControlProgram {
	override fun input(frame: Int, observed: MovementSimulationState): MovementSimulationInput =
		MovementSimulationInput(rotation = Rotation(heldYaw, observed.rotation.pitch))
}
