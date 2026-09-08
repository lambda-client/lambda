package com.lambda.pathing.actions

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
			// A partial hold releases forward after the solved tick count; the rest
			// of the arc coasts, exactly as the solver flew it.
			forward = if (solution.holdForward && flightTicks < solution.holdTicks) 1.0 else 0.0
		} else if (standingStart) {
			// A standing launch: creep to the lip, come to rest, then launch from rest;
			// there is no entry speed to reproduce. A body that ARRIVES moving (a landing
			// on the pad) may carry more speed than the remaining run to the lip can shed
			// by friction: it brakes, as a player would, instead of coasting off the edge.
			val along = alongEdge(takeoff, aim, observed.position.x, observed.position.z)
			val speed = observed.velocity.horizontalLength()
			val runout = (speed + CREEP_TAP_SPEED) * COAST_RUNOUT_TICKS
			if (along + runout < solution.launchOffset) {
				forward = 1.0 // a tap from here still coasts to rest before the lip
			} else if (speed > solution.speedSlack && !launchedStanding) {
				// Rolling out the last of the creep -- unless this is arrival speed the run
				// to the lip cannot shed: then brake. The creep's own taps never exceed
				// BRAKE_MIN_SPEED, so a standing start from rest is unaffected.
				val coast = speed * COAST_RUNOUT_TICKS
				val overshoots = along + coast > solution.launchOffset + BRAKE_OVERSHOOT_BLOCKS
				forward = if (speed > BRAKE_MIN_SPEED && overshoots) -1.0 else 0.0
			} else {
				// At rest on the lip: this tick is the model's launch tick.
				launchedStanding = true
				jump = solution.jump
				forward = if (solution.holdForward) 1.0 else 0.0
			}
		} else {
			// A moving-entry launch: regulate toward the solved speed, and press
			// jump on the last tick still standing on the lip -- the same
			// one-step-lookahead trigger RunUpLaunchProgram uses.
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

		// A standing start creeps WITHOUT sprint (a sprint tap moves nearly a quarter
		// block, off the lip); the sprint-jump boost needs the flag ON THE LAUNCH TICK
		// only. See docs/decisions/launch-solver.md (standing starts).
		val sprint = solution.sprint && forward > 0.0 &&
				(!standingStart || launchedStanding || airborne)
		// Lateral correction in flight is proportional and, while forward is held, capped:
		// the game normalises the input vector, so a full strafe alongside a full forward
		// costs 30% of the forward acceleration a marginal arc needs to reach its pad.
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

		/** Lateral error at which the strafe would be full; smaller errors get a proportional share. */
		const val LATERAL_FULL_OFFSET = 0.5

		/** Strafe allowed while forward is held: keeps the normalised forward component above 0.95. */
		const val HELD_FORWARD_MAX_STRAFE = 0.33

		const val LIP_MARGIN = LIP_LOOKAHEAD_MARGIN

		/** Arrival speed above which a standing start brakes rather than coasts; creep taps stay below it. */
		const val BRAKE_MIN_SPEED = 0.12

		/** Coast past the launch offset tolerated before braking; the body still stands on the block. */
		const val BRAKE_OVERSHOOT_BLOCKS = 0.15

		/** One walk tap's speed; with [COAST_RUNOUT_TICKS] it bounds a creep step's travel. */
		const val CREEP_TAP_SPEED = 0.06

		/** Ground friction geometric runout: 1 / (1 - 0.91 * 0.6). */
		const val COAST_RUNOUT_TICKS = 2.2
	}
}
