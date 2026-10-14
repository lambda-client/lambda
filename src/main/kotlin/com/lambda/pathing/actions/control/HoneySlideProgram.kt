package com.lambda.pathing.actions.control

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.actions.ControlProgram
import com.lambda.pathing.actions.ProgramContext
import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.core.center
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState
import kotlin.math.cos
import kotlin.math.sin

internal class HoneySlideProgram(private val decision: TrajectoryDecision.HoneySlide, context: ProgramContext) : ControlProgram {
	private val maxYawChange = context.constraints.maxYawDegreesPerFrame
	private val takeoff = context.body.stance.center()
	private val radians = Math.toRadians(decision.yaw)
	private val forwardX = -sin(radians)
	private val forwardZ = cos(radians)
	private val wallX = cos(radians) * decision.strafe
	private val wallZ = sin(radians) * decision.strafe
	private var launchedAt: Int? = null
	private var touching = false

	override fun input(frame: Int, observed: MovementSimulationState): MovementSimulationInput {
		val yawDelta = Rotation.wrap(decision.yaw - observed.rotation.yaw).coerceIn(-maxYawChange, maxYawChange)
		val offsetX = observed.position.x - takeoff.x
		val offsetZ = observed.position.z - takeoff.z
		val jump = launchedAt == null && observed.onGround && offsetX * forwardX + offsetZ * forwardZ >= decision.jumpAlong
		if (jump) launchedAt = frame
		val launched = launchedAt
		val halfWidth = (observed.boundingBox.maxX - observed.boundingBox.minX) / 2.0
		if (launched != null && offsetX * wallX + offsetZ * wallZ >= WALL_FACE - halfWidth - CONTACT_EPSILON) touching = true
		val strafe = if (launched != null && !touching && !observed.onGround && frame - launched >= decision.strafeDelay) decision.strafe else 0.0
		return MovementSimulationInput(
			forward = 1.0,
			strafe = strafe,
			jump = jump,
			sprint = true,
			rotation = Rotation(observed.rotation.yaw + yawDelta, observed.rotation.pitch),
		)
	}

	private companion object {
		const val WALL_FACE = 0.5 + 1 / 16.0

		const val CONTACT_EPSILON = 1.0e-3
	}
}
