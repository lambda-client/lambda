package com.lambda.pathing.actions.control

import com.lambda.pathing.actions.ControlProgram
import com.lambda.pathing.actions.ProgramContext
import com.lambda.pathing.actions.TrajectoryDecision

import com.lambda.pathing.core.alongEdge
import com.lambda.pathing.core.center
import com.lambda.pathing.core.horizontalDistance
import com.lambda.pathing.core.interpolate
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState

internal class DropProgram(decision: TrajectoryDecision.Drop, context: ProgramContext) : ControlProgram {
	private val solution = decision.solution
	private val climbOut = decision.climbOut
	private val origin = context.body.stance.center()
	private val takeoff = interpolate(context.body.stance.center(), decision.step.center(), solution.launchOffset)
	private val aim = interpolate(context.body.stance.center(), decision.step.center(), solution.aimDistance)
	private val maxYawChange = context.constraints.maxYawDegreesPerFrame
	private val flightLength = horizontalDistance(takeoff, aim)

	private var airborne = false
	private var landed = false
	private var aligned = false
	private var pinnedTicks = 0
	private var slowestPinnedSpeed = Double.POSITIVE_INFINITY
	private var previousAlong = Double.NaN
	private var previousStep = 0.0

	override fun input(frame: Int, observed: MovementSimulationState): MovementSimulationInput {
		if (!observed.onGround) airborne = true else if (airborne) landed = true

		val rotation = rotationToward(aim, observed, maxYawChange)

		val sneak = !airborne && pinningToLip(observed)

		val forward = when {
			landed -> 0.0
			airborne -> airborneForward(observed)

			aligned && !solution.holdForward -> 0.0

			else -> 1.0
		}

		return MovementSimulationInput(
			forward = forward,
			strafe = if (airborne && !landed) airborneStrafe(takeoff, aim, observed) else 0.0,
			sprint = solution.sprint && forward > 0.0 && !sneak,
			sneak = sneak,
			jump = climbOut && !landed && alongEdge(origin, aim, observed.position.x, observed.position.z) < CLIMB_OUT_ALONG,
			rotation = rotation,
		)
	}

	private fun pinningToLip(observed: MovementSimulationState): Boolean {
		if (aligned) return false
		if (!atLip(observed)) return false

		val speed = observed.velocity.horizontalLength()
		if (speed <= solution.speed + SPEED_TOLERANCE) {
			aligned = true
			return false
		}

		if (pinnedTicks >= POSE_LAG_TICKS && speed > slowestPinnedSpeed - PIN_CONVERGENCE) {
			aligned = true
			return false
		}

		pinnedTicks++
		slowestPinnedSpeed = minOf(slowestPinnedSpeed, speed)
		return true
	}

	private fun atLip(observed: MovementSimulationState): Boolean {
		val along = alongEdge(takeoff, aim, observed.position.x, observed.position.z)
		val step = if (previousAlong.isNaN()) 0.0 else along - previousAlong
		previousAlong = along
		previousStep = maxOf(step, previousStep * STEP_DECAY)

		val reach = maxOf(previousStep, observed.velocity.horizontalLength())
		return -along <= reach + LIP_MARGIN
	}

	private fun airborneForward(observed: MovementSimulationState): Double {
		if (!solution.holdForward && !climbOut) return 0.0
		val travelled = alongEdge(takeoff, aim, observed.position.x, observed.position.z)
		return if (travelled < flightLength) 1.0 else 0.0
	}

	private companion object {

		const val SPEED_TOLERANCE = 0.015

		const val PIN_CONVERGENCE = 1e-4

		const val POSE_LAG_TICKS = 2

		const val STEP_DECAY = 0.9

		const val LIP_MARGIN = 0.1

		const val CLIMB_OUT_ALONG = 0.5
	}
}
