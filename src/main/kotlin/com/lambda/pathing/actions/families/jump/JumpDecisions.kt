package com.lambda.pathing.actions.families.jump

import com.lambda.pathing.actions.CoarseMoveRates
import com.lambda.pathing.actions.DecisionContext
import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.actions.closingSpeed
import com.lambda.pathing.actions.nominalLaunchFrame
import com.lambda.pathing.actions.reachableEntrySpeed
import com.lambda.pathing.core.center
import com.lambda.pathing.core.Via
import com.lambda.pathing.core.plus
import com.lambda.pathing.core.unitBetween
import com.lambda.pathing.launch.BallisticProfile
import com.lambda.pathing.launch.LaunchMode
import com.lambda.pathing.launch.LaunchSolution
import com.lambda.pathing.launch.LaunchSolver
import com.lambda.pathing.world.canStandAt
import kotlin.math.floor
import kotlin.math.hypot
import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.actions.MAX_LAUNCH_FRAME
import com.lambda.pathing.core.alongEdge
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

internal object JumpDecisions {

	private val ballistics = BallisticProfile.VANILLA

	fun solutionsFor(context: DecisionContext): List<LaunchSolution> {
		val onward = onwardEntryWindow(context)
		val dodge = context.edge.launch?.lateralOffset ?: 0.0
		val via = context.edge.via
			?: return solve(context, onward, null, 0.0, context.edge.launch).map { if (dodge != 0.0 && it.lateralOffset == 0.0) it.copy(lateralOffset = dodge) else it }
		val bent = solve(context, onward, via, dodge, context.edge.launch)
		if (dodge == 0.0) return bent
		return bent + solve(context, onward, null, 0.0, null).map { it.copy(lateralOffset = dodge) }
	}

	private fun solve(
		context: DecisionContext,
		onward: ClosedFloatingPointRange<Double>?,
		via: Via?,
		landingShift: Double,
		fallback: LaunchSolution?,
	): List<LaunchSolution> {
		val ideal = onward?.let { window ->
			LaunchSolver.solve(
				context.edge.from, context.edge.to,
				modes = JumpTemplates.MODES, exitSpeedWindow = window, via = via, landingShift = landingShift,
			).firstOrNull()
		} ?: fallback ?: hopOver(context)
		val asIs = LaunchSolver.solve(
			context.edge.from, context.edge.to,
			modes = JumpTemplates.MODES,
			maxEntrySpeed = { context.body.speed },
			preferredEntrySpeed = { context.body.speed },
			exitSpeedWindow = onward,
			via = via,
			landingShift = landingShift,
		).firstOrNull()
		return listOfNotNull(ideal, asIs)
	}

	private fun onwardEntryWindow(context: DecisionContext): ClosedFloatingPointRange<Double>? {
		val onward = context.steering
			.chain(context.edge.to, null, ONWARD_LOOKAHEAD, null)
			.getOrNull(1)
			?: return null
		if (onward == context.edge.to) return null
		if (hypot((onward.x - context.edge.to.x).toDouble(), (onward.z - context.edge.to.z).toDouble()) <
			CoarseMoveRates.MIN_JUMP_DISTANCE
		) return null
		val solutions = LaunchSolver.solve(
			context.edge.to, onward, modes = JumpTemplates.MODES,
		)
		if (solutions.isEmpty()) return null
		val entry = solutions.minOf { it.speed - it.speedSlack }..
				solutions.maxOf { it.speed + it.speedSlack }

		return ballistics.groundReachable(entry, PAD_GROUND_TICKS, sprint = true)
	}

	private fun hopOver(context: DecisionContext): LaunchSolution? {
		val edge = context.edge
		if (edge.to.y < edge.from.y) return null
		val reachable = reachableEntrySpeed(context)
		return LaunchSolver.solve(
			edge.from, edge.to,
			modes = JumpTemplates.MODES,
			maxEntrySpeed = { reachable },
		).firstOrNull()
	}

	fun launchDelays(context: DecisionContext, solution: LaunchSolution): List<Int> {
		val edge = context.edge
		val from = edge.from.center()
		val to = solution.via?.let { from + it } ?: edge.to.center()
		val (unitX, unitZ) = unitBetween(from, to) ?: return emptyList()

		val ballistics = BallisticProfile.VANILLA
		val acceleration = ballistics.groundAcceleration(solution.sprint)
		val friction = ballistics.groundFriction
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
					delay + misplacement / ballistics.cruiseSpeed(solution.sprint),
					misplacement,
					abs(speed - solution.speed),
				)
			}

			val yawError = Rotation.wrap(bearing - yaw)
			yaw += yawError.coerceIn(-context.constraints.maxYawDegreesPerFrame, context.constraints.maxYawDegreesPerFrame)
			val radians = Math.toRadians(yaw)
			val forwardX = -sin(radians)
			val forwardZ = cos(radians)
			velocityX = (velocityX + acceleration * forwardX) * friction
			velocityZ = (velocityZ + acceleration * forwardZ) * friction
			along += (velocityX * unitX + velocityZ * unitZ) / friction
		}

		if (fitting.isEmpty()) {
			val nominal = nominalLaunchFrame(context, solution)
			val needed = (solution.speed - solution.speedSlack)..(solution.speed + solution.speedSlack)
			return LAUNCH_BRACKET.map { (nominal + it).coerceAtLeast(0) }
				.distinct()
				.filterNot { delay ->
					val reach = ballistics.groundSpeedReach(body.velocity.x, body.velocity.z, unitX, unitZ, delay, solution.sprint)
					reach.endInclusive < needed.start || reach.start > needed.endInclusive
				}
		}

		val cheapest = fitting.sortedBy { it.cost }.map { it.delay }
		val truest = fitting.sortedBy { it.misplacement }.map { it.delay }
		val fastest = fitting.sortedBy { it.speedError }.map { it.delay }
		return (cheapest.take(2) + truest.take(2) + fastest.take(2)).distinct()
	}

	fun runUpDecisions(context: DecisionContext, solutions: List<LaunchSolution>): List<TrajectoryDecision> {
		val edge = context.edge
		val closing = closingSpeed(context)
		val anyReachable = solutions.any { solution ->
			ballistics.runUpSpeed(closing, nominalLaunchFrame(context, solution), solution.sprint) >= minimumSpeed(solution)
		}
		if (anyReachable) return emptyList()

		val decisions = ArrayList<TrajectoryDecision>()
		for (solution in solutions) {
			val groundNeeded = ballistics.runUpDistanceFor(solution.speed, solution.sprint)
			if (groundNeeded != null) {
				val retreatAlong = solution.launchOffset - (groundNeeded + RUN_UP_MARGIN_BLOCKS)
				if (clearBehind(context, -retreatAlong)) {
					decisions += TrajectoryDecision.RunUpLaunch(edge.to, solution, retreatAlong, hopAlong = null)
				}
				continue
			}

			val mode = if (solution.sprint) LaunchMode.SPRINT_JUMP else LaunchMode.WALK_JUMP
			val cruise = ballistics.cruiseSpeed(solution.sprint)
			val hop = ballistics.fly(mode, cruise, 0.0) ?: continue
			if (hop.exitSpeed < minimumSpeed(solution) - HOP_EXIT_TOLERANCE) continue
			val groundToCruise = ballistics.runUpDistanceFor(cruise * CRUISE_FRACTION, solution.sprint)
				?: continue
			for (gap in HOP_LANDING_GAPS) {
				val hopAlong = solution.launchOffset - hop.distance - gap
				val retreatAlong = hopAlong - groundToCruise - RUN_UP_MARGIN_BLOCKS
				if (!clearBehind(context, -retreatAlong)) continue
				decisions += TrajectoryDecision.RunUpLaunch(edge.to, solution, retreatAlong, hopAlong)
			}
		}
		return decisions.take(MAX_RUN_UP_VARIANTS)
	}

	private fun minimumSpeed(solution: LaunchSolution) = solution.speed - solution.speedSlack * REQUIRED_SLACK_FRACTION

	private fun clearBehind(context: DecisionContext, distance: Double): Boolean {
		if (distance <= 0.0) return true
		val edge = context.edge
		val from = edge.from.center()
		val to = edge.to.center()
		val (unitX, unitZ) = unitBetween(from, to) ?: return false
		val view = context.view
		var sampled = 1.0
		while (sampled <= distance + 1.0) {
			val x = floor(from.x - unitX * sampled).toInt()
			val z = floor(from.z - unitZ * sampled).toInt()
			if (!view.canStandAt(x, edge.from.y, z)) return false
			sampled += 1.0
		}
		return true
	}

	private const val RUN_UP_MARGIN_BLOCKS = 0.75

	private const val REQUIRED_SLACK_FRACTION = 0.5

	private const val CRUISE_FRACTION = 0.97

	private const val HOP_EXIT_TOLERANCE = 0.02

	private val HOP_LANDING_GAPS = listOf(-0.35, -0.1, 0.2)

	private const val MAX_RUN_UP_VARIANTS = 4

	private const val ONWARD_LOOKAHEAD = 2

	private const val PAD_GROUND_TICKS = 3

	private val LAUNCH_BRACKET = listOf(0, -1, 1)

	private const val FOOTPRINT_OVERHANG = 0.3
}
