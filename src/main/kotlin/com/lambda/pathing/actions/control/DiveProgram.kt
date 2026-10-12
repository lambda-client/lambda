package com.lambda.pathing.actions.control

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.actions.ControlProgram
import com.lambda.pathing.actions.ProgramContext
import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.physics.Vanilla
import kotlin.math.atan2
import kotlin.math.hypot

internal class DiveProgram(decision: TrajectoryDecision.Dive, context: ProgramContext) : ControlProgram {
	private var breathing = decision.breathe
	private val maxYawChange = context.constraints.maxYawDegreesPerFrame
	private val sprint = context.constraints.sprintModes.first()
	private val targetX = decision.step.x + 0.5
	private val targetY = decision.step.y + BODY_CENTER
	private val targetZ = decision.step.z + 0.5
	private val pocketX = context.body.stance.x + 0.5
	private val pocketZ = context.body.stance.z + 0.5

	override fun input(frame: Int, observed: MovementSimulationState): MovementSimulationInput {
		if (breathing && observed.air < Vanilla.MAX_AIR) return breathe(observed)
		breathing = false
		val dx = targetX - observed.position.x
		val dz = targetZ - observed.position.z
		val dy = targetY - (observed.position.y + BODY_CENTER)
		val yaw = Math.toDegrees(atan2(dz, dx)) - 90.0
		val pitch = (-Math.toDegrees(atan2(dy, maxOf(hypot(dx, dz), MIN_HORIZONTAL)))).coerceIn(-MAX_PITCH, MAX_PITCH)
		val step = yawStepTowards(yaw, observed, maxYawChange)
		return MovementSimulationInput(
			forward = 1.0,
			sprint = sprint,
			jump = dy > RISE_WITH_JUMP && !observed.swimming,
			sneak = sprint && dy <= 0.0 && !observed.swimming && !observed.onGround,
			rotation = Rotation(observed.rotation.yaw + step, pitchTowards(pitch, observed, maxYawChange)),
		)
	}

	private fun breathe(observed: MovementSimulationState): MovementSimulationInput {
		val dx = pocketX - observed.position.x
		val dz = pocketZ - observed.position.z
		if (hypot(dx, dz) <= POCKET_CENTERING) return MovementSimulationInput(jump = true, rotation = observed.rotation)
		val yaw = Math.toDegrees(atan2(dz, dx)) - 90.0
		return MovementSimulationInput(
			forward = CENTERING_INPUT,
			jump = true,
			rotation = Rotation(observed.rotation.yaw + yawStepTowards(yaw, observed, maxYawChange), observed.rotation.pitch),
		)
	}

	private companion object {
		const val BODY_CENTER = 0.3
		const val POCKET_CENTERING = 0.15
		const val CENTERING_INPUT = 0.3
		const val MAX_PITCH = 75.0
		const val MIN_HORIZONTAL = 0.35
		const val RISE_WITH_JUMP = 0.4
	}
}
