package com.lambda.pathing.actions

import com.lambda.pathing.core.alongEdge
import com.lambda.pathing.core.center
import com.lambda.pathing.launch.BallisticProfile
import com.lambda.pathing.launch.LaunchSolution

internal fun nominalLaunchFrame(context: DecisionContext, solution: LaunchSolution): Int {
	val edge = context.edge
	val from = edge.from.center()
	val to = edge.to.center()
	val body = context.body.state
	val remaining = solution.launchOffset - alongEdge(from, to, body.position.x, body.position.z)
	if (remaining <= 0.0) return 0
	return BallisticProfile.VANILLA.groundRunUpTicks(
		entrySpeed = closingSpeed(context),
		distance = remaining,
		sprint = solution.sprint,
		maxTicks = MAX_LAUNCH_FRAME,
	)
}

internal fun closingSpeed(context: DecisionContext): Double {
	val edge = context.edge
	val from = edge.from.center()
	val to = edge.to.center()
	val body = context.body.state
	return alongEdge(
		from, to,
		from.x + body.velocity.x,
		from.z + body.velocity.z,
	).coerceAtLeast(0.0)
}

internal fun reachableEntrySpeed(context: DecisionContext): Double =
	maxOf(context.body.speed, BallisticProfile.VANILLA.cruiseSpeed(sprint = true))

internal const val MAX_LAUNCH_FRAME = 8

fun DecisionContext.landingRisk(solution: LaunchSolution): Double {
	if (view.voxel(edge.to.x, edge.to.y - 1, edge.to.z).bouncy) return 0.0
	val limit = constraints.maxSafeFallDistance
	if (limit <= 0.0) return 1.0
	val rise = (edge.to.y - edge.from.y).toDouble()
	val fall = solution.fallDistance(rise)
	val safe = limit - ARC_MODEL_ERROR_BLOCKS
	return ((fall - safe) / ARC_MODEL_ERROR_BLOCKS).coerceIn(0.0, 1.0)
}

private const val ARC_MODEL_ERROR_BLOCKS = 1.0
