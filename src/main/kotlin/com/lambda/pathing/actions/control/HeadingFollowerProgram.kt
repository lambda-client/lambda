package com.lambda.pathing.actions.control

import com.lambda.pathing.actions.LaunchTrigger

import com.lambda.pathing.actions.ControlProgram

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.core.MovementKeys
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState

internal class HeadingFollowerProgram(
	private val targetYaw: Double,
	private val sprint: Boolean,
	private val maxYawChange: Double,
	private val launch: LaunchTrigger?,
	private val keys: MovementKeys,
	private val airborneKeys: MovementKeys,
) : ControlProgram {
	override fun input(frame: Int, observed: MovementSimulationState): MovementSimulationInput {
		val yawDelta = yawStepTowards(targetYaw, observed, maxYawChange)
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
