package com.lambda.pathing.actions.control

import com.lambda.pathing.actions.ControlProgram

import com.lambda.pathing.core.HorizontalPoint
import com.lambda.pathing.core.alongEdge
import com.lambda.pathing.launch.LaunchSolution
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState

internal class RunUpLaunchProgram(
	private val takeoff: HorizontalPoint,
	private val aim: HorizontalPoint,
	private val solution: LaunchSolution,
	private val retreatAlong: Double,
	hopAlong: Double?,
	private val hopHoldDelay: Int,
	private val edgeAlong: Double?,
	private val maxYawChange: Double,
) : ControlProgram {
	private val jumpTargets = ArrayDeque(listOfNotNull(hopAlong, solution.launchOffset))

	private var retreatDone = false
	private var launched = false
	private var landedAfterLaunch = false
	private var hopFrame: Int? = null

	override fun input(frame: Int, observed: MovementSimulationState): MovementSimulationInput {
		val along = alongEdge(takeoff, aim, observed.position.x, observed.position.z)
		if (launched && observed.onGround && along > solution.launchOffset) landedAfterLaunch = true

		val rotation = rotationToward(aim, observed, maxYawChange)

		if (landedAfterLaunch) return MovementSimulationInput(rotation = rotation)
		if (launched && !observed.onGround) {
			return MovementSimulationInput(
				forward = if (solution.holdForward) 1.0 else 0.0,
				strafe = airborneStrafe(takeoff, aim, observed),
				sprint = solution.sprint && solution.holdForward,
				rotation = rotation,
			)
		}

		if (!retreatDone) {
			if (along > retreatAlong && observed.onGround) {
				return MovementSimulationInput(forward = -1.0, rotation = rotation)
			}
			retreatDone = true
		}

		val velocityAlong = alongEdge(takeoff, aim, observed.position.x + observed.velocity.x, observed.position.z + observed.velocity.z) - along
		var jump = false
		if (observed.onGround && jumpTargets.isNotEmpty()) {
			val launching = jumpTargets.size == 1
			val due = when {
				launching && edgeAlong != null -> along > edgeAlong
				launching || hopHoldDelay == 0 -> reachesLip(takeoff, aim, observed, along, jumpTargets.first())
				else -> velocityAlong >= 0.0 && reachesLip(takeoff, aim, observed, along, jumpTargets.first())
			}
			if (due) {
				jump = true
				if (launching) launched = true else hopFrame = frame
				jumpTargets.removeFirst()
			}
		}
		val holding = hopFrame?.let { frame - it >= hopHoldDelay } ?: true
		return MovementSimulationInput(
			forward = if (holding) 1.0 else 0.0,
			sprint = solution.sprint,
			jump = jump,
			rotation = rotation,
		)
	}
}
