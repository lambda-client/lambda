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
	private val maxYawChange: Double,
) : ControlProgram {
	private val jumpTargets = ArrayDeque(listOfNotNull(hopAlong, solution.launchOffset))

	private var retreatDone = false
	private var launched = false
	private var landedAfterLaunch = false

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

		var jump = false
		if (observed.onGround && jumpTargets.isNotEmpty()) {
			if (reachesLip(takeoff, aim, observed, along, jumpTargets.first())) {
				jump = true
				if (jumpTargets.size == 1) launched = true
				jumpTargets.removeFirst()
			}
		}
		return MovementSimulationInput(
			forward = 1.0,
			sprint = solution.sprint,
			jump = jump,
			rotation = rotation,
		)
	}
}
