package com.lambda.pathing.actions.control

import com.lambda.pathing.actions.ControlProgram

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.core.HorizontalPoint
import com.lambda.pathing.core.alongEdge
import com.lambda.pathing.launch.BounceSolution
import com.lambda.pathing.launch.BounceSolver
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState

internal class BounceProgram(
	private val takeoff: HorizontalPoint,
	private val aim: HorizontalPoint,
	private val solution: BounceSolution,
	private val maxYawChange: Double,
) : ControlProgram {
	private var airborne = false
	private var landed = false
	private var bounced = false
	private var flightTicks = 0

	override fun input(frame: Int, observed: MovementSimulationState): MovementSimulationInput {

		if (!observed.onGround) {
			airborne = true
			flightTicks++
		} else if (airborne) {
			if (observed.velocity.y > REBOUND_SPEED) bounced = true else landed = true
		}

		val desiredYaw = yawTowards(aim, observed)
		val yawDelta = yawStepTowards(desiredYaw, observed, maxYawChange)
		val rotation = Rotation(observed.rotation.yaw + yawDelta, observed.rotation.pitch)

		var jump = false
		val forward: Double
		if (landed) {
			forward = 0.0
		} else if (airborne) {

			forward = if (solution.holdForward && flightTicks < solution.holdTicks) 1.0 else 0.0
		} else if (standingStart) {

			val along = alongEdge(takeoff, aim, observed.position.x, observed.position.z)
			val speed = observed.velocity.horizontalLength()
			val runout = (speed + CREEP_TAP_SPEED) * COAST_RUNOUT_TICKS
			if (along + runout < solution.launchOffset) {
				forward = 1.0
			} else if (speed > solution.speedSlack && !launchedStanding) {

				val coast = speed * COAST_RUNOUT_TICKS
				val overshoots = along + coast > solution.launchOffset + BRAKE_OVERSHOOT_BLOCKS
				forward = if (speed > BRAKE_MIN_SPEED && overshoots) -1.0 else 0.0
			} else {

				launchedStanding = true
				jump = solution.jump
				forward = if (solution.holdForward) 1.0 else 0.0
			}
		} else {

			if (solution.jump) {
				val along = alongEdge(takeoff, aim, observed.position.x, observed.position.z)
				val nextAlong = alongEdge(
					takeoff, aim,
					observed.position.x + observed.velocity.x,
					observed.position.z + observed.velocity.z,
				)
				val reach = (nextAlong - along).coerceAtLeast(0.0)
				if (solution.launchOffset - along <= reach + LIP_MARGIN) jump = true
			}
			forward = when {
				jump -> if (solution.holdForward) 1.0 else 0.0
				else -> approachForward(observed)
			}
		}

		val sprint = solution.sprint && forward > 0.0 &&
				(!standingStart || launchedStanding || airborne)

		val strafe = if (airborne && !landed) {
			val proportional = (lateralOffset(takeoff, aim, observed) / LATERAL_FULL_OFFSET).coerceIn(-1.0, 1.0)
			val limit = if (forward > 0.0) HELD_FORWARD_MAX_STRAFE else 1.0
			if (kotlin.math.abs(proportional) < LATERAL_DEADBAND / LATERAL_FULL_OFFSET) 0.0 else proportional.coerceIn(-limit, limit)
		} else 0.0
		return MovementSimulationInput(
			forward = forward,
			strafe = strafe,
			sprint = sprint,
			jump = jump,
			sneak = false,
			rotation = rotation,
		)
	}

	private val standingStart get() = solution.speed <= BounceSolver.STANDING_REST_SPEED

	private var launchedStanding = false

	private fun approachForward(observed: MovementSimulationState): Double =
		if (observed.velocity.horizontalLength() >= solution.speed + solution.speedSlack) 0.0 else 1.0

	private companion object {

		const val REBOUND_SPEED = 0.1

		const val LATERAL_DEADBAND = 0.05

		const val LATERAL_FULL_OFFSET = 0.5

		const val HELD_FORWARD_MAX_STRAFE = 0.33

		const val LIP_MARGIN = LIP_LOOKAHEAD_MARGIN

		const val BRAKE_MIN_SPEED = 0.12

		const val BRAKE_OVERSHOOT_BLOCKS = 0.15

		const val CREEP_TAP_SPEED = 0.06

		const val COAST_RUNOUT_TICKS = 2.2
	}
}
