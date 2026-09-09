package com.lambda.pathing.actions.control

import com.lambda.pathing.actions.ControlProgram

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.core.HorizontalPoint
import com.lambda.pathing.core.alongEdge
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState

internal class ClimbProgram(
	private val targetYaw: Double?,
	private val holdForward: Boolean,
	private val holdWhileClimbing: Boolean,
	private val climbWithJump: Boolean,
	private val maxYawChange: Double,
	private val takeoff: HorizontalPoint? = null,
	private val aim: HorizontalPoint? = null,
) : ControlProgram {

	private val overLip = holdForward && !holdWhileClimbing && !climbWithJump

	override fun input(frame: Int, observed: MovementSimulationState): MovementSimulationInput {
		val yawDelta = targetYaw?.let { target ->
			Rotation.wrap(target - observed.rotation.yaw).coerceIn(-maxYawChange, maxYawChange)
		} ?: 0.0

		val aligned = targetYaw == null ||
				kotlin.math.abs(Rotation.wrap(targetYaw - observed.rotation.yaw)) <= ALIGNMENT_DEGREES

		val forward = if (observed.onGround) {
			if (overLip && !aligned) {
				0.0
			} else if (overLip && takeoff != null && aim != null) {

				val along = alongEdge(takeoff, aim, observed.position.x, observed.position.z)
				val speed = observed.velocity.horizontalLength()
				when {
					along + (speed + CREEP_TAP_SPEED) * COAST_RUNOUT_TICKS < LIP_ALONG -> 1.0
					speed > CREEP_REST_SPEED -> 0.0
					else -> 1.0
				}
			} else if (holdForward) 1.0 else 0.0
		} else if (holdWhileClimbing) {
			1.0
		} else if (overLip && observed.velocity.horizontalLength() > DRIFT_REST_SPEED) {

			-1.0
		} else {
			0.0
		}

		return MovementSimulationInput(
			forward = forward,
			sprint = false,

			jump = climbWithJump,
			rotation = Rotation(observed.rotation.yaw + yawDelta, observed.rotation.pitch),
		)
	}

	private companion object {

		const val CREEP_TAP_SPEED = 0.06
		const val COAST_RUNOUT_TICKS = 2.2
		const val CREEP_REST_SPEED = 0.03

		const val LIP_ALONG = 0.8

		const val DRIFT_REST_SPEED = 0.02

		const val ALIGNMENT_DEGREES = 25.0
	}
}
