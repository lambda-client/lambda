package com.lambda.pathing.actions.control

import com.lambda.pathing.actions.ControlProgram
import com.lambda.pathing.actions.MotionConstraints

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.core.DEGENERATE_LENGTH
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.center
import com.lambda.pathing.core.horizontalDistance
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState
import kotlin.math.abs
import kotlin.math.hypot

data class TerminalApproach(
	val sprint: Boolean,
	val brakeDistance: Double,
	val stepUpJumpLeadDistance: Double?,
)

internal class CorridorFollowerProgram(
	stanceNodes: List<Stance>,
	private val parameters: TerminalApproach,
	private val config: MotionConstraints,
) : ControlProgram {
	private val maxYawChange = config.maxYawDegreesPerFrame
	private val pursuit = PursuitTracker(stanceNodes.map { it.center() })
	private val nodes = pursuit.nodes

	private val rises = if (parameters.stepUpJumpLeadDistance == null) {
		emptyList()
	} else {
		stanceNodes.zipWithNext().mapIndexedNotNull { index, (from, to) ->
			index.takeIf { to.y > from.y }
		}
	}

	private val distanceToGoal = DoubleArray(nodes.size).also { suffix ->
		for (index in nodes.lastIndex - 1 downTo 0) {
			suffix[index] = suffix[index + 1] + horizontalDistance(nodes[index], nodes[index + 1])
		}
	}

	private var braking = false
	private var terminalApproach = false
	private var terminalReleased = false
	private var nextRise = 0
	private var jumpWasAirborne = false

	override fun input(frame: Int, observed: MovementSimulationState): MovementSimulationInput {
		pursuit.advance(observed)

		val jump = shouldJump(observed)

		val risePending = nextRise < rises.size

		if (terminalApproach) return terminalApproachInput(observed, jump)

		if (braking) {
			val stoppedShort = observed.onGround &&
					observed.velocity.horizontalLength() <= MotionConstraints.STOPPED_SPEED &&
					hypot(
						nodes.last().x - observed.position.x,
						nodes.last().z - observed.position.z,
					) > config.goalRadius
			if (stoppedShort) {
				braking = false
				terminalApproach = true
				return terminalApproachInput(observed, jump)
			}
			return coastInput(observed, jump)
		}

		val coastStop = observed.velocity.horizontalLength() * GROUND_COAST_PER_SPEED +
				BRAKE_SHORT_BIAS_BLOCKS
		if (!risePending &&
			remainingPathDistance(observed) <= maxOf(parameters.brakeDistance, coastStop)
		) {
			braking = true
			return coastInput(observed, jump)
		}

		return MovementSimulationInput(
			forward = 1.0,
			sprint = parameters.sprint,
			jump = jump,
			rotation = rotationToward(pursuit.target(PursuitTracker.DEFAULT_LOOK_AHEAD_NODES), observed, maxYawChange),
		)
	}

	private fun coastInput(observed: MovementSimulationState, jump: Boolean) =
		MovementSimulationInput(rotation = observed.rotation, jump = jump)

	private fun terminalApproachInput(
		observed: MovementSimulationState,
		jump: Boolean,
	): MovementSimulationInput {
		val goal = nodes.last()
		val dx = goal.x - observed.position.x
		val dz = goal.z - observed.position.z
		val distance = hypot(dx, dz)

		val speedTowardGoal = if (distance <= DEGENERATE_LENGTH) 0.0
		else (observed.velocity.x * dx + observed.velocity.z * dz) / distance
		val coast = speedTowardGoal * GROUND_COAST_PER_SPEED

		if (terminalReleased || distance <= config.goalRadius || coast >= distance) {
			terminalReleased = true
			return coastInput(observed, jump)
		}
		val yawError = Rotation.wrap(yawTowards(goal, observed) - observed.rotation.yaw)
		val yawDelta = yawError.coerceIn(-maxYawChange, maxYawChange)

		val forward = if (abs(yawError) > TERMINAL_PIVOT_YAW_DEGREES) 0.0 else 1.0
		return MovementSimulationInput(
			forward = forward,
			sprint = false,
			jump = jump,
			rotation = Rotation(observed.rotation.yaw + yawDelta, observed.rotation.pitch),
		)
	}

	private fun remainingPathDistance(observed: MovementSimulationState): Double {
		val next = minOf(nodes.lastIndex, pursuit.progressIndex + 1)
		val toNext = hypot(nodes[next].x - observed.position.x, nodes[next].z - observed.position.z)
		return toNext + distanceToGoal[next]
	}

	private fun shouldJump(observed: MovementSimulationState): Boolean {
		val leadDistance = parameters.stepUpJumpLeadDistance ?: return false
		if (nextRise >= rises.size) return false
		if (!observed.onGround) {
			jumpWasAirborne = true
			return false
		}
		if (jumpWasAirborne) {
			nextRise++
			jumpWasAirborne = false
			if (nextRise >= rises.size) return false
		}
		val takeoff = nodes[rises[nextRise]]
		return hypot(takeoff.x - observed.position.x, takeoff.z - observed.position.z) <= leadDistance
	}

	private companion object {
		const val TERMINAL_PIVOT_YAW_DEGREES = 45.0

		const val BRAKE_SHORT_BIAS_BLOCKS = 0.08
	}
}
