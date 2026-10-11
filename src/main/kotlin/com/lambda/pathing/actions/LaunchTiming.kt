package com.lambda.pathing.actions

import com.lambda.pathing.core.alongEdge
import com.lambda.pathing.core.center
import com.lambda.pathing.launch.BallisticProfile
import com.lambda.pathing.launch.LaunchSolution

internal fun nominalLaunchFrame(context: DecisionContext, solution: LaunchSolution): Int =
	BallisticProfile.VANILLA.groundRunUpTicks(
		entrySpeed = closingSpeed(context),
		distance = solution.launchOffset - context.along(context.body.state.position.x, context.body.state.position.z),
		sprint = solution.sprint,
		maxTicks = MAX_LAUNCH_FRAME,
	)

internal fun closingSpeed(context: DecisionContext): Double {
	val velocity = context.body.state.velocity
	return context.along(context.edge.from.x + 0.5 + velocity.x, context.edge.from.z + 0.5 + velocity.z).coerceAtLeast(0.0)
}

private fun DecisionContext.along(x: Double, z: Double): Double = alongEdge(edge.from.center(), edge.to.center(), x, z)

internal fun reachableEntrySpeed(context: DecisionContext): Double =
	maxOf(context.body.speed, BallisticProfile.VANILLA.cruiseSpeed(sprint = true))

internal const val MAX_LAUNCH_FRAME = 8

fun DecisionContext.landingRisk(solution: LaunchSolution): Double {
	if (view.voxel(edge.to.x, edge.to.y - 1, edge.to.z).bouncy) return 0.0
	val limit = constraints.maxFallOnto(view.fallRule(edge.to.x, edge.to.y - 1, edge.to.z))
	if (limit <= 0.0) return 1.0
	val rise = (edge.to.y - edge.from.y).toDouble()
	val fall = solution.fallDistance(rise)
	val safe = limit - ARC_MODEL_ERROR_BLOCKS
	return ((fall - safe) / ARC_MODEL_ERROR_BLOCKS).coerceIn(0.0, 1.0)
}

private const val ARC_MODEL_ERROR_BLOCKS = 1.0
