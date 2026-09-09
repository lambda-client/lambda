package com.lambda.pathing.actions.families.jump

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.actions.nominalLaunchFrame
import com.lambda.pathing.actions.MAX_LAUNCH_FRAME
import com.lambda.pathing.actions.DecisionContext
import com.lambda.pathing.core.alongEdge
import com.lambda.pathing.core.center
import com.lambda.pathing.launch.HorizontalDynamics
import com.lambda.pathing.launch.LaunchSolution

internal object JumpLaunchDelays {

	fun launchDelays(context: DecisionContext, solution: LaunchSolution): List<Int> {
		val edge = context.edge
		val from = edge.from.center()
		val to = edge.to.center()
		val length = kotlin.math.hypot(to.x - from.x, to.z - from.z)
		if (length <= 1e-9) return emptyList()
		val unitX = (to.x - from.x) / length
		val unitZ = (to.z - from.z) / length

		val dynamics = HorizontalDynamics.ground(context.ballistics, solution.sprint)
		val body = context.body.state
		var velocityX = body.velocity.x
		var velocityZ = body.velocity.z
		var along = alongEdge(from, to, body.position.x, body.position.z)

		val bearing = Math.toDegrees(kotlin.math.atan2(-(to.x - from.x), to.z - from.z))
		var yaw = body.rotation.yaw

		class Fit(val delay: Int, val cost: Double, val misplacement: Double, val speedError: Double)

		val standableAlong = 0.5 * (kotlin.math.abs(unitX) + kotlin.math.abs(unitZ)) + 0.3

		val fitting = ArrayList<Fit>()
		for (delay in 0..MAX_LAUNCH_FRAME) {
			if (along > standableAlong) break
			val speed = velocityX * unitX + velocityZ * unitZ
			if (kotlin.math.abs(speed - solution.speed) <= solution.speedSlack) {

				val misplacement = kotlin.math.abs(along - solution.launchOffset)
				fitting += Fit(
					delay,
					delay + misplacement / dynamics.cruise,
					misplacement,
					kotlin.math.abs(speed - solution.speed),
				)
			}

			val yawError = Rotation.wrap(bearing - yaw)
			yaw += yawError.coerceIn(-context.constraints.maxYawDegreesPerFrame, context.constraints.maxYawDegreesPerFrame)
			val radians = Math.toRadians(yaw)
			val forwardX = -kotlin.math.sin(radians)
			val forwardZ = kotlin.math.cos(radians)
			velocityX = (velocityX + dynamics.acceleration * forwardX) * dynamics.friction
			velocityZ = (velocityZ + dynamics.acceleration * forwardZ) * dynamics.friction
			along += (velocityX * unitX + velocityZ * unitZ) / dynamics.friction
		}

		if (fitting.isEmpty()) {
			val nominal = nominalLaunchFrame(context, solution)
			return LAUNCH_BRACKET.map { (nominal + it).coerceAtLeast(0) }
				.distinct()
				.filter { !unreachableEntry(context, it, solution) }
		}

		val cheapest = fitting.sortedBy { it.cost }.map { it.delay }
		val truest = fitting.sortedBy { it.misplacement }.map { it.delay }
		val fastest = fitting.sortedBy { it.speedError }.map { it.delay }
		return (cheapest.take(2) + truest.take(2) + fastest.take(2))
			.distinct()
			.take(MAX_LAUNCH_DELAY_CANDIDATES)
	}

	private fun unreachableEntry(
		context: DecisionContext,
		delay: Int,
		solution: LaunchSolution,
	): Boolean {
		val edge = context.edge
		val from = edge.from.center()
		val to = edge.to.center()
		val length = kotlin.math.hypot(to.x - from.x, to.z - from.z)
		if (length <= 1e-9) return false
		val unitX = (to.x - from.x) / length
		val unitZ = (to.z - from.z) / length
		val velocity = context.body.state.velocity
		val along = HorizontalDynamics.ground(context.ballistics, solution.sprint)
			.reachable(velocity.x, velocity.z, delay)
			.projectOnto(unitX, unitZ)
		val needed = (solution.speed - solution.speedSlack)..(solution.speed + solution.speedSlack)
		return along.endInclusive < needed.start || along.start > needed.endInclusive
	}

	private val LAUNCH_BRACKET = listOf(0, -1, 1)

	private const val MAX_LAUNCH_DELAY_CANDIDATES = 6

}
