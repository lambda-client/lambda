package com.lambda.pathing.actions.control

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.actions.ControlProgram
import com.lambda.pathing.actions.ProgramContext
import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState
import kotlin.math.atan2
import kotlin.math.hypot

internal class SwimProgram(private val decision: TrajectoryDecision.Swim, context: ProgramContext) : ControlProgram {
	private val maxYawChange = context.constraints.maxYawDegreesPerFrame
	private val pursuit = PursuitTracker(context.nodes)

	override fun input(frame: Int, observed: MovementSimulationState): MovementSimulationInput {
		pursuit.advance(observed)
		if (decision.stroke) return MovementSimulationInput(
			forward = 1.0,
			sprint = true,
			sneak = !observed.swimming,
			rotation = Rotation(observed.rotation.yaw + yawStepTowards(decision.yaw ?: observed.rotation.yaw, observed, maxYawChange), pitchTowards(STROKE_PITCH, observed, maxYawChange)),
		)
		val rotation = decision.yaw?.let { Rotation(observed.rotation.yaw + yawStepTowards(it, observed, maxYawChange), observed.rotation.pitch) }
			?: rotationToward(pursuit.target(PursuitTracker.DEFAULT_LOOK_AHEAD_NODES), observed, maxYawChange)
		if (observed.swimming) {
			val climbing = decision.movement == MovementId.EXIT_WATER && decision.step.y - observed.position.y > EXIT_CLIMB
			return MovementSimulationInput(
				forward = 1.0,
				sprint = decision.sprint,
				jump = climbing,
				rotation = Rotation(rotation.yaw, pitchTowards(if (climbing) EXIT_PITCH else swimPitch(observed), observed, maxYawChange)),
			)
		}
		return MovementSimulationInput(
			forward = 1.0,
			jump = observed.touchingWater,
			sprint = decision.sprint && !observed.eyeInWater,
			rotation = rotation,
		)
	}

	private fun swimPitch(observed: MovementSimulationState): Double {
		if (decision.movement != MovementId.EXIT_WATER) return STROKE_PITCH
		val dx = decision.step.x + 0.5 - observed.position.x
		val dz = decision.step.z + 0.5 - observed.position.z
		val dy = decision.step.y - observed.position.y
		return (-Math.toDegrees(atan2(dy, maxOf(hypot(dx, dz), MIN_HORIZONTAL)))).coerceIn(-MAX_PITCH, MAX_PITCH)
	}

	private companion object {
		const val STROKE_PITCH = -30.0
		const val EXIT_PITCH = -60.0
		const val EXIT_CLIMB = 0.2
		const val MAX_PITCH = 75.0
		const val MIN_HORIZONTAL = 0.35
	}
}
