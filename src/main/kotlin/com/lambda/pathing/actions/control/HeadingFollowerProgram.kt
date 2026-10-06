package com.lambda.pathing.actions.control

import com.lambda.pathing.actions.ControlProgram

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.actions.ProgramContext
import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState

internal class HeadingFollowerProgram(private val decision: TrajectoryDecision.Heading, context: ProgramContext) : ControlProgram {
	private val maxYawChange = context.constraints.maxYawDegreesPerFrame
	private val launch = context.launch
	private val sprint = decision.sprint && decision.keys.sustainsSprint

	override fun input(frame: Int, observed: MovementSimulationState): MovementSimulationInput {
		val yawDelta = yawStepTowards(decision.yaw, observed, maxYawChange)
		val held = if (observed.onGround) decision.keys else decision.airborneKeys
		return MovementSimulationInput(
			forward = held.forward,
			strafe = held.strafe,
			sprint = sprint,
			jump = launch?.press(observed) == true,
			rotation = Rotation(observed.rotation.yaw + yawDelta, observed.rotation.pitch),
		)
	}
}
