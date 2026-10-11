package com.lambda.pathing.actions.control

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.actions.ControlProgram
import com.lambda.pathing.actions.ProgramContext
import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.core.alongEdge
import com.lambda.pathing.core.center
import com.lambda.pathing.launch.AirPlan
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState

internal class FallProgram(decision: TrajectoryDecision.Fall, context: ProgramContext) : ControlProgram {
	private val solution = decision.solution
	private val origin = context.body.stance.center()
	private val aim = decision.step.center()
	private val maxYawChange = context.constraints.maxYawDegreesPerFrame
	private val airPlan = AirPlan.toward(context.body.stance, decision.step, solution, 0.0)

	private var airborne = false
	private var airborneTicks = 0
	private var heldYaw = Double.NaN

	override fun input(frame: Int, observed: MovementSimulationState): MovementSimulationInput {
		if (!observed.onGround) airborne = true
		if (airborne && observed.touchingWater) {
			if (heldYaw.isNaN()) heldYaw = observed.rotation.yaw
			return MovementSimulationInput(jump = true, rotation = Rotation(heldYaw, observed.rotation.pitch))
		}

		val rotation = rotationToward(aim, observed, maxYawChange)
		if (airborne && !observed.onGround) {
			airborneTicks++
			val steered = airPlan?.steer(observed, rotation.yaw, airborneTicks)
			val forward = steered?.forward ?: if (airborneTicks <= solution.heldForwardTicks) 1.0 else 0.0
			return MovementSimulationInput(
				forward = forward,
				strafe = steered?.strafe ?: 0.0,
				sprint = solution.sprint && forward > 0.0,
				rotation = rotation,
			)
		}

		val along = alongEdge(origin, aim, observed.position.x, observed.position.z)
		val jump = solution.jumps && !airborne && reachesLip(origin, aim, observed, along, solution.launchOffset)
		return MovementSimulationInput(
			forward = if (airborne) 0.0 else 1.0,
			sprint = solution.sprint && !airborne,
			jump = jump,
			rotation = rotation,
		)
	}
}
