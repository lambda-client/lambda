package com.lambda.pathing.actions.families.jump

import com.lambda.pathing.actions.CoarseMoveRates
import com.lambda.pathing.actions.DecisionContext
import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.actions.closingSpeed
import com.lambda.pathing.actions.nominalLaunchFrame
import com.lambda.pathing.actions.reachableEntrySpeed
import com.lambda.pathing.core.center
import com.lambda.pathing.core.unitBetween
import com.lambda.pathing.launch.BallisticProfile
import com.lambda.pathing.launch.LaunchMode
import com.lambda.pathing.launch.LaunchSolution
import com.lambda.pathing.launch.LaunchSolver
import com.lambda.pathing.world.canStandAt
import kotlin.math.floor
import kotlin.math.hypot

internal object JumpDecisions {

	private val ballistics = BallisticProfile.VANILLA

	fun solutionsFor(context: DecisionContext): List<LaunchSolution> {
		val onward = onwardEntryWindow(context)

		val ideal = onward?.let { window ->
			LaunchSolver.best(
				context.edge.from, context.edge.to,
				modes = JumpTemplates.MODES, exitSpeedWindow = window,
			)
		} ?: context.edge.launch ?: hopOver(context)
		val asIs = LaunchSolver.best(
			context.edge.from, context.edge.to,
			modes = JumpTemplates.MODES,
			maxEntrySpeed = { context.body.speed },
			preferredEntrySpeed = { context.body.speed },
			exitSpeedWindow = onward,
		)

		val dodge = context.edge.launch?.lateralOffset ?: 0.0
		return listOfNotNull(ideal, asIs).map { solution ->
			if (dodge != 0.0 && solution.lateralOffset == 0.0) solution.copy(lateralOffset = dodge)
			else solution
		}
	}

	private fun onwardEntryWindow(context: DecisionContext): ClosedFloatingPointRange<Double>? {
		val steering = context.steering ?: return null
		val onward = steering
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
		return LaunchSolver.best(
			edge.from, edge.to,
			modes = JumpTemplates.MODES,
			maxEntrySpeed = { reachable },
		)
	}

	fun runUpDecisions(context: DecisionContext, solutions: List<LaunchSolution>): List<TrajectoryDecision> {
		if (solutions.isEmpty()) return emptyList()
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
					decisions += TrajectoryDecision.RunUpLaunch(
						solution.sprint, edge.to, solution, retreatAlong, hopAlong = null,
					)
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
				decisions += TrajectoryDecision.RunUpLaunch(
					solution.sprint, edge.to, solution, retreatAlong, hopAlong,
				)
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
}
