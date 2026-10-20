package com.lambda.pathing.actions.control

import com.lambda.pathing.actions.ControlProgram
import com.lambda.pathing.actions.ProgramContext
import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.core.DEGENERATE_LENGTH
import com.lambda.pathing.core.alongEdge
import com.lambda.pathing.core.center
import com.lambda.pathing.core.plus
import com.lambda.pathing.launch.BounceSolver
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.physics.Vanilla
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

internal class BounceProgram(decision: TrajectoryDecision.Bounce, context: ProgramContext) : ControlProgram {
	private val takeoff = context.body.stance.center()
	private val aim = decision.step.center()
	private val solution = decision.solution
	private val approach = solution.via?.let { takeoff + it } ?: aim
	private val maxYawChange = context.constraints.maxYawDegreesPerFrame
	private val standingStart = solution.speed <= BounceSolver.STANDING_REST_SPEED
	private var airborne = false
	private var landed = false
	private var launchedStanding = false
	private var flightTicks = 0
	private var pressedForward = false
	private var contacts = 0
	private var grounded = false

	override fun input(frame: Int, observed: MovementSimulationState): MovementSimulationInput {
		if (!observed.onGround) {
			airborne = true
			flightTicks++
		} else if (airborne) {
			if (observed.velocity.y <= Vanilla.SLIME_STEP_MAX_VERTICAL_SPEED) landed = true else if (!grounded) contacts++
		}
		grounded = observed.onGround
		val target = if (contacts == 0) approach else aim
		val segmentStart = if (contacts == 0 || solution.via == null) takeoff else approach

		val rotation = rotationToward(target, observed, maxYawChange)

		var jump = false
		val forward: Double
		if (landed) {
			forward = 0.0
		} else if (airborne) {
			forward = if (solution.holdForward && (flightTicks < solution.holdTicks || solution.via != null && contacts > 0)) 1.0 else 0.0
		} else if (standingStart) {
			val along = alongEdge(takeoff, approach, observed.position.x, observed.position.z)
			val speed = observed.velocity.horizontalLength()
			val runout = (speed + CREEP_TAP_SPEED) * GROUND_COAST_PER_SPEED
			if (along + runout < solution.launchOffset) {
				forward = 1.0
			} else if (speed > solution.speedSlack && !launchedStanding) {
				val coast = speed * GROUND_COAST_PER_SPEED
				val overshoots = along + coast > solution.launchOffset + BRAKE_OVERSHOOT_BLOCKS
				forward = if (speed > BRAKE_MIN_SPEED && overshoots) -1.0 else 0.0
			} else {
				launchedStanding = true
				jump = solution.jump
				forward = if (solution.holdForward) 1.0 else 0.0
			}
		} else {
			if (solution.jump) {
				val along = alongEdge(takeoff, approach, observed.position.x, observed.position.z)
				if (reachesLip(takeoff, approach, observed, along, solution.launchOffset)) jump = true
			}
			forward = when {
				jump -> if (solution.holdForward) 1.0 else 0.0
				observed.velocity.horizontalLength() >= solution.speed + solution.speedSlack -> 0.0
				else -> 1.0
			}
		}
		val resetsDoubleTap = !solution.sprint && !airborne && pressedForward && forward == 0.0 && observed.doubleTapSprintTicks > 0
		pressedForward = forward > 0.0

		val sprint = solution.sprint && forward > 0.0 &&
				(!standingStart || launchedStanding || airborne)

		val strafe = if (airborne && !landed) {
			val proportional = (lateralOffset(segmentStart, target, observed) / LATERAL_FULL_OFFSET).coerceIn(-1.0, 1.0)
			val limit = if (forward > 0.0) HELD_FORWARD_MAX_STRAFE else 1.0
			if (abs(proportional) < LATERAL_DEADBAND / LATERAL_FULL_OFFSET) 0.0 else proportional.coerceIn(-limit, limit)
		} else 0.0
		if (solution.via != null && contacts > 0 && airborne && !landed) {
			val towardX = aim.x - observed.position.x
			val towardZ = aim.z - observed.position.z
			val length = hypot(towardX, towardZ)
			if (length > DEGENERATE_LENGTH) {
				val yaw = Math.toRadians(rotation.yaw)
				val steerForward = (-towardX * sin(yaw) + towardZ * cos(yaw)) / length
				val steerStrafe = (towardX * cos(yaw) + towardZ * sin(yaw)) / length
				return MovementSimulationInput(
					forward = steerForward,
					strafe = steerStrafe,
					sprint = solution.sprint && steerForward > 0.0,
					rotation = rotation,
				)
			}
		}
		return MovementSimulationInput(
			forward = forward,
			strafe = strafe,
			sprint = sprint,
			jump = jump,
			sneak = resetsDoubleTap,
			rotation = rotation,
		)
	}

	private companion object {

		const val LATERAL_DEADBAND = 0.05

		const val LATERAL_FULL_OFFSET = 0.5

		const val HELD_FORWARD_MAX_STRAFE = 0.33

		const val BRAKE_MIN_SPEED = 0.12

		const val BRAKE_OVERSHOOT_BLOCKS = 0.15
	}
}
