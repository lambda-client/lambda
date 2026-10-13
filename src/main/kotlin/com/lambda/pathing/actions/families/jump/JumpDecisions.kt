package com.lambda.pathing.actions.families.jump

import com.lambda.pathing.actions.CoarseMoveRates
import com.lambda.pathing.actions.DecisionContext
import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.actions.closingSpeed
import com.lambda.pathing.actions.nominalLaunchFrame
import com.lambda.pathing.actions.reachableEntrySpeed
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.center
import com.lambda.pathing.core.Via
import com.lambda.pathing.core.plus
import com.lambda.pathing.core.unitBetween
import com.lambda.pathing.launch.BODY_HALF_WIDTH
import com.lambda.pathing.launch.BallisticProfile
import com.lambda.pathing.launch.LaunchMode
import com.lambda.pathing.launch.LaunchSolution
import com.lambda.pathing.launch.LaunchSolver
import com.lambda.pathing.world.canStandAt
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.pow
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
		val via = context.edge.via ?: run {
			val solutions = solve(context, onward, null, 0.0, context.edge.launch)
			if (dodge == 0.0) return solutions
			return solutions.map { if (it.lateralOffset == 0.0) it.copy(lateralOffset = dodge) else it } + solutions.filter { it.lateralOffset == 0.0 }
		}
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
			solved(SolveKey(context.edge.from, context.edge.to, window, via, landingShift, context.headroom, null)).firstOrNull()
		} ?: fallback ?: hopOver(context)
		val asIs = LaunchSolver.solve(
			context.edge.from, context.edge.to,
			modes = JumpTemplates.MODES,
			maxEntrySpeed = { context.body.speed },
			preferredEntrySpeed = { context.body.speed },
			exitSpeedWindow = onward,
			via = via,
			landingShift = landingShift,
			headroom = context.headroom,
		).firstOrNull()
		return listOfNotNull(ideal, asIs)
	}

	private fun onwardEntryWindow(context: DecisionContext): ClosedFloatingPointRange<Double>? {
		val onward = context.steering
			.chain(context.edge.to, null, ONWARD_LOOKAHEAD)
			.getOrNull(1)
			?: return null
		if (onward == context.edge.to) return null
		if (hypot((onward.x - context.edge.to.x).toDouble(), (onward.z - context.edge.to.z).toDouble()) <
			CoarseMoveRates.MIN_JUMP_DISTANCE
		) return null
		val solutions = solved(SolveKey(context.edge.to, onward, null, null, 0.0, Double.POSITIVE_INFINITY, null))
		if (solutions.isEmpty()) return null
		val entry = solutions.minOf { it.speed - it.speedSlack }..
				solutions.maxOf { it.speed + it.speedSlack }

		return ballistics.groundReachable(entry, PAD_GROUND_TICKS, sprint = true)
	}

	private fun hopOver(context: DecisionContext): LaunchSolution? {
		val edge = context.edge
		if (edge.to.y < edge.from.y) return null
		val reachable = reachableEntrySpeed(context)
		if (reachable > CRUISE_ENTRY_SPEED) {
			return LaunchSolver.solve(edge.from, edge.to, modes = JumpTemplates.MODES, maxEntrySpeed = { reachable }, headroom = context.headroom).firstOrNull()
		}
		return solved(SolveKey(edge.from, edge.to, null, null, 0.0, context.headroom, reachable)).firstOrNull()
	}

	private data class SolveKey(
		val from: Stance,
		val to: Stance,
		val exitSpeedWindow: ClosedFloatingPointRange<Double>?,
		val via: Via?,
		val landingShift: Double,
		val headroom: Double,
		val maxEntrySpeed: Double?,
	)

	private val solutions = ConcurrentHashMap<SolveKey, List<LaunchSolution>>()

	private fun solved(key: SolveKey): List<LaunchSolution> {
		solutions[key]?.let { return it }
		if (solutions.size > SOLUTION_CACHE_LIMIT) solutions.clear()
		return solutions.computeIfAbsent(key) {
			val maxEntrySpeed = key.maxEntrySpeed
			if (maxEntrySpeed == null) {
				LaunchSolver.solve(key.from, key.to, modes = JumpTemplates.MODES, exitSpeedWindow = key.exitSpeedWindow, via = key.via, landingShift = key.landingShift, headroom = key.headroom)
			} else {
				LaunchSolver.solve(key.from, key.to, modes = JumpTemplates.MODES, maxEntrySpeed = { maxEntrySpeed }, exitSpeedWindow = key.exitSpeedWindow, via = key.via, landingShift = key.landingShift, headroom = key.headroom)
			}
		}
	}

	private val DecisionContext.headroom: Double get() = edge.launch?.headroom ?: Double.POSITIVE_INFINITY

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
		var lateral = (body.position.x - from.x) * -unitZ + (body.position.z - from.z) * unitX

		val bearing = Math.toDegrees(atan2(-(to.x - from.x), to.z - from.z))
		var yaw = body.rotation.yaw

		class Fit(val delay: Int, val cost: Double, val misplacement: Double, val speedError: Double)

		val halfExtent = 0.5 * (abs(unitX) + abs(unitZ))
		val standableAlong = halfExtent + FOOTPRINT_OVERHANG
		val landingPerSpeed = if (solution.speedSlack > 0.0) solution.landingSlack / solution.speedSlack else Double.POSITIVE_INFINITY
		val lateralCarry = 1.0 + friction * (1.0 - AIR_DRAG.pow((solution.airTicks - 1).coerceAtLeast(0))) / (1.0 - AIR_DRAG)

		val fitting = ArrayList<Fit>()
		var edgeDelay: Int? = null
		for (delay in 0..MAX_LAUNCH_FRAME) {
			if (along > standableAlong) {
				edgeDelay = delay
				break
			}
			val speed = velocityX * unitX + velocityZ * unitZ
			val speedError = minOf(
				abs(speed - solution.speed),
				abs(speed - solution.speed + (along - solution.launchOffset) / landingPerSpeed),
			)
			val drift = lateral + (velocityX * -unitZ + velocityZ * unitX) * lateralCarry - solution.lateralOffset
			val landsBeside = solution.via == null && abs(drift) > halfExtent + BODY_HALF_WIDTH
			if (speedError <= solution.speedSlack && !landsBeside) {
				val misplacement = abs(along - solution.launchOffset)
				fitting += Fit(
					delay,
					delay + misplacement / ballistics.cruiseSpeed(solution.sprint),
					misplacement,
					speedError,
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
			lateral += (velocityX * -unitZ + velocityZ * unitX) / friction
		}

		val underCeiling = !context.view.voxel(edge.from.x, edge.from.y + 2, edge.from.z).fullyPassable
		val offEdge = if (underCeiling && edgeDelay != null) listOf(edgeDelay, edgeDelay + 1) else emptyList()

		if (fitting.isEmpty()) {
			val nominal = nominalLaunchFrame(context, solution)
			val needed = (solution.speed - solution.speedSlack)..(solution.speed + solution.speedSlack)
			return (LAUNCH_BRACKET.map { (nominal + it).coerceAtLeast(0) }
				.distinct()
				.filterNot { delay ->
					val reach = ballistics.groundSpeedReach(body.velocity.x, body.velocity.z, unitX, unitZ, delay, solution.sprint)
					reach.endInclusive < needed.start || reach.start > needed.endInclusive
				} + offEdge).distinct()
		}

		val cheapest = fitting.sortedBy { it.cost }.map { it.delay }
		val truest = fitting.sortedBy { it.misplacement }.map { it.delay }
		val fastest = fitting.sortedBy { it.speedError }.map { it.delay }
		return (cheapest.take(2) + truest.take(2) + fastest.take(2) + offEdge).distinct()
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

	private const val SOLUTION_CACHE_LIMIT = 1 shl 15

	private val CRUISE_ENTRY_SPEED = BallisticProfile.VANILLA.cruiseSpeed(sprint = true)

	private const val AIR_DRAG = BallisticProfile.HORIZONTAL_DRAG
}
