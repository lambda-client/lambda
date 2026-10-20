package com.lambda.pathing.actions.control

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.actions.ControlProgram
import com.lambda.pathing.actions.ProgramContext
import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState

internal class HeadingFollowerProgram(private val decision: TrajectoryDecision.Heading, context: ProgramContext) : ControlProgram {
	private val turning = context.constraints.turning
	private val launch = context.launch
	private var pivoting = decision.pivot

	override fun input(frame: Int, observed: MovementSimulationState): MovementSimulationInput {
		val rotation = turning.yawTo(observed, decision.yaw, decision.turnRate)
		if (pivoting && !turning.reached(rotation, Rotation(decision.yaw, rotation.pitch))) return MovementSimulationInput(rotation = rotation)
		pivoting = false
		val held = if (observed.onGround) decision.keys else decision.airborneKeys
		return MovementSimulationInput(
			forward = held.forward,
			strafe = held.strafe,
			sprint = decision.sprint,
			jump = launch?.press(observed) == true,
			rotation = rotation,
		)
	}
}
