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
	private val turning = context.constraints.turning
	private val pursuit = PursuitTracker(context.nodes)

	override fun input(frame: Int, observed: MovementSimulationState): MovementSimulationInput {
		pursuit.advance(observed)
		if (decision.stroke) return MovementSimulationInput(
			forward = 1.0,
			sprint = true,
			sneak = !observed.swimming,
			rotation = turning.turn(observed, Rotation(decision.yaw ?: observed.rotation.yaw, STROKE_PITCH)),
		)
		val yaw = decision.yaw ?: yawTowards(pursuit.target(PursuitTracker.DEFAULT_LOOK_AHEAD_NODES), observed)
		if (observed.swimming) {
			val climbing = decision.movement == MovementId.EXIT_WATER && decision.step.y - observed.position.y > EXIT_CLIMB
			return MovementSimulationInput(
				forward = 1.0,
				sprint = decision.sprint,
				jump = climbing,
				rotation = turning.turn(observed, Rotation(yaw, if (climbing) EXIT_PITCH else swimPitch(observed))),
			)
		}
		return MovementSimulationInput(
			forward = 1.0,
			jump = observed.touchingWater,
			sprint = decision.sprint && !observed.eyeInWater,
			rotation = turning.yawTo(observed, yaw),
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
