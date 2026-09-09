package com.lambda.pathing.actions.control

import com.lambda.pathing.core.Stance
import com.lambda.pathing.launch.AirSteering
import com.lambda.pathing.launch.BallisticProfile
import com.lambda.pathing.launch.LaunchSolution
import kotlin.math.hypot

internal fun airPlanToward(
	from: Stance,
	to: Stance,
	solution: LaunchSolution,
	lateralOffset: Double,
): AirSteering.AirPlan? {
	val dx = (to.x - from.x).toDouble()
	val dz = (to.z - from.z).toDouble()
	val length = hypot(dx, dz)
	if (length <= 1e-9) return null
	val unitX = dx / length
	val unitZ = dz / length
	return AirSteering.AirPlan(
		aimX = to.x + 0.5 - unitZ * lateralOffset,
		aimZ = to.z + 0.5 + unitX * lateralOffset,
		unitX = unitX,
		unitZ = unitZ,
		airTicks = solution.airTicks,
		holdTicks = if (solution.holdForward) solution.holdTicks else 0,
		sprintAcceleration = if (solution.sprint) BallisticProfile.SPRINT_AIR_ACCELERATION
		else BallisticProfile.WALK_AIR_ACCELERATION,
		walkAcceleration = BallisticProfile.WALK_AIR_ACCELERATION,
	)
}
