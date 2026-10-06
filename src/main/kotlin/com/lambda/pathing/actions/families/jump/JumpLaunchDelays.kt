package com.lambda.pathing.actions.families.jump

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.actions.nominalLaunchFrame
import com.lambda.pathing.actions.MAX_LAUNCH_FRAME
import com.lambda.pathing.actions.DecisionContext
import com.lambda.pathing.core.alongEdge
import com.lambda.pathing.core.center
import com.lambda.pathing.core.unitBetween
import com.lambda.pathing.launch.BallisticProfile
import com.lambda.pathing.launch.HorizontalDynamics
import com.lambda.pathing.launch.LaunchSolution
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

internal object JumpLaunchDelays {

	fun launchDelays(context: DecisionContext, solution: LaunchSolution): List<Int> {
		val edge = context.edge
		val from = edge.from.center()
		val to = edge.to.center()
		val (unitX, unitZ) = unitBetween(from, to) ?: return emptyList()

		val dynamics = HorizontalDynamics.ground(BallisticProfile.VANILLA, solution.sprint)
		val body = context.body.state
		var velocityX = body.velocity.x
		var velocityZ = body.velocity.z
		var along = alongEdge(from, to, body.position.x, body.position.z)

		val bearing = Math.toDegrees(atan2(-(to.x - from.x), to.z - from.z))
		var yaw = body.rotation.yaw

		class Fit(val delay: Int, val cost: Double, val misplacement: Double, val speedError: Double)

		val standableAlong = 0.5 * (abs(unitX) + abs(unitZ)) + FOOTPRINT_OVERHANG

		val fitting = ArrayList<Fit>()
		for (delay in 0..MAX_LAUNCH_FRAME) {
			if (along > standableAlong) break
			val speed = velocityX * unitX + velocityZ * unitZ
			if (abs(speed - solution.speed) <= solution.speedSlack) {

				val misplacement = abs(along - solution.launchOffset)
				fitting += Fit(
					delay,
					delay + misplacement / dynamics.cruise,
					misplacement,
					abs(speed - solution.speed),
				)
			}

			val yawError = Rotation.wrap(bearing - yaw)
			yaw += yawError.coerceIn(-context.constraints.maxYawDegreesPerFrame, context.constraints.maxYawDegreesPerFrame)
			val radians = Math.toRadians(yaw)
			val forwardX = -sin(radians)
			val forwardZ = cos(radians)
			velocityX = (velocityX + dynamics.acceleration * forwardX) * dynamics.friction
			velocityZ = (velocityZ + dynamics.acceleration * forwardZ) * dynamics.friction
			along += (velocityX * unitX + velocityZ * unitZ) / dynamics.friction
		}

		if (fitting.isEmpty()) {
			val nominal = nominalLaunchFrame(context, solution)
			val needed = (solution.speed - solution.speedSlack)..(solution.speed + solution.speedSlack)
			return LAUNCH_BRACKET.map { (nominal + it).coerceAtLeast(0) }
				.distinct()
				.filterNot { delay ->
					val reach = dynamics.reachable(body.velocity.x, body.velocity.z, delay).projectOnto(unitX, unitZ)
					reach.endInclusive < needed.start || reach.start > needed.endInclusive
				}
		}

		val cheapest = fitting.sortedBy { it.cost }.map { it.delay }
		val truest = fitting.sortedBy { it.misplacement }.map { it.delay }
		val fastest = fitting.sortedBy { it.speedError }.map { it.delay }
		return (cheapest.take(2) + truest.take(2) + fastest.take(2))
			.distinct()
			.take(MAX_LAUNCH_DELAY_CANDIDATES)
	}

	private val LAUNCH_BRACKET = listOf(0, -1, 1)

	private const val MAX_LAUNCH_DELAY_CANDIDATES = 6

	private const val FOOTPRINT_OVERHANG = 0.3

}
