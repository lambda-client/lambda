package com.lambda.pathing.actions.control

import com.lambda.pathing.actions.ControlProgram

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.actions.ProgramContext
import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState
import kotlin.math.abs

internal class HeadingFollowerProgram(private val decision: TrajectoryDecision.Heading, context: ProgramContext) : ControlProgram {
	private val maxYawChange = minOf(context.constraints.maxYawDegreesPerFrame, decision.turnRate)
	private val launch = context.launch
	private var pivoting = decision.pivot

	override fun input(frame: Int, observed: MovementSimulationState): MovementSimulationInput {
		val yawDelta = yawStepTowards(decision.yaw, observed, maxYawChange)
		val rotation = Rotation(observed.rotation.yaw + yawDelta, observed.rotation.pitch)
		if (pivoting && abs(Rotation.wrap(decision.yaw - rotation.yaw)) > PIVOT_ALIGNED_DEGREES) return MovementSimulationInput(rotation = rotation)
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

	private companion object {
		const val PIVOT_ALIGNED_DEGREES = 1e-6
	}
}
