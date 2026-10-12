package com.lambda.pathing.actions.control

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.actions.ControlProgram
import com.lambda.pathing.actions.ProgramContext
import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState

internal class SwimProgram(private val decision: TrajectoryDecision.Swim, context: ProgramContext) : ControlProgram {
	private val maxYawChange = context.constraints.maxYawDegreesPerFrame
	private val pursuit = PursuitTracker(context.nodes)

	override fun input(frame: Int, observed: MovementSimulationState): MovementSimulationInput {
		pursuit.advance(observed)
		val rotation = decision.yaw?.let { Rotation(observed.rotation.yaw + yawStepTowards(it, observed, maxYawChange), observed.rotation.pitch) }
			?: rotationToward(pursuit.target(PursuitTracker.DEFAULT_LOOK_AHEAD_NODES), observed, maxYawChange)
		return MovementSimulationInput(
			forward = 1.0,
			jump = observed.touchingWater,
			sprint = decision.sprint && !observed.eyeInWater,
			rotation = rotation,
		)
	}
}
