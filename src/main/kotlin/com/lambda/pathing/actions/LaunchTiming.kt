package com.lambda.pathing.actions

import com.lambda.pathing.core.alongEdge
import com.lambda.pathing.core.center
import com.lambda.pathing.launch.LaunchSolution

internal fun nominalLaunchFrame(context: DecisionContext, solution: LaunchSolution): Int {
	val edge = context.edge
	val from = edge.from.center()
	val to = edge.to.center()
	val body = context.body.state
	val remaining = solution.launchOffset - alongEdge(from, to, body.position.x, body.position.z)
	if (remaining <= 0.0) return 0

	val closing = alongEdge(
		from, to,
		from.x + body.velocity.x,
		from.z + body.velocity.z,
	).coerceAtLeast(0.0)

	return context.ballistics.groundRunUpTicks(
		entrySpeed = closing,
		distance = remaining,
		sprint = solution.sprint,
		maxTicks = MAX_LAUNCH_FRAME,
	)
}

internal const val MAX_LAUNCH_FRAME = 8
