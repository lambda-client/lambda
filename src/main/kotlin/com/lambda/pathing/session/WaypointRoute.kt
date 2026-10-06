package com.lambda.pathing.session

import com.lambda.Lambda.LOG
import com.lambda.context.Automated
import com.lambda.pathing.core.ArrivalMode
import com.lambda.pathing.core.Stance
import com.lambda.pathing.search.PublishedPath
import com.lambda.pathing.session.PathingSession.Companion.PATHING_SOURCE
import com.lambda.util.CommunicationUtils.info
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.util.math.BlockPos

internal class WaypointRoute {

	private class Group(val through: List<Stance>, val goal: Stance) {
		val size: Int get() = through.size + 1
	}

	private val queue = ArrayDeque<Group>()
	private var automated: Automated? = null

	var expectedLeg: Stance? = null
		private set

	private var nextJourney: PlanningJourney? = null

	val queuedWaypoints: Int get() = queue.sumOf { it.size }

	fun start(automated: Automated, waypoints: List<Stance>): PathingRequest? {
		drop()
		if (waypoints.isEmpty()) return null
		this.automated = automated
		val groups = if (automated.pathingConfig.waypointArrival == ArrivalMode.WALK_THROUGH) {
			listOf(Group(waypoints.dropLast(1), waypoints.last()))
		} else {
			waypoints.map { Group(emptyList(), it) }
		}
		val first = groups.first()
		queue.addAll(groups.drop(1))
		expectedLeg = first.goal
		return PathingRequest(automated, first.goal, first.through).submit()
	}

	fun drop() {
		queue.clear()
		automated = null
		expectedLeg = null
		nextJourney?.cancel()
		nextJourney = null
	}

	fun adoptWarmJourney(resolvedGoal: Stance): PlanningJourney? {
		val warmed = nextJourney?.takeIf { it.goal == resolvedGoal } ?: return null
		nextJourney = null
		return warmed
	}

	fun primeNextLeg(player: ClientPlayerEntity, request: PathingRequest, running: PublishedPath?) {
		val next = queue.firstOrNull() ?: return
		if (running == null || running.partial) return
		val terminal = running.plan.frames.lastOrNull()?.state ?: return
		val resolved = resolveGoalStance(player, next.goal)
		if (nextJourney?.goal == resolved) return
		nextJourney?.cancel()
		nextJourney = null

		val preparation = when (
			val prepared = preparePlanning(player, request, next.goal, next.through, initialOverride = terminal)
		) {
			is PlanningPreparationResult.Ready -> prepared.preparation
			else -> return
		}
		nextJourney = PlanningJourney.open(preparation, player)
		LOG.info("Pre-warming the next route leg toward {}", preparation.finalGoal)
	}

	fun continueNext() {
		val automated = automated ?: return
		val next = queue.removeFirstOrNull() ?: return drop()
		expectedLeg = next.goal
		info(
			"Route: continuing to ${next.goal}" +
					(next.through.size.takeIf { it > 0 }?.let { " through $it waypoint(s)" } ?: "") +
					(queuedWaypoints.takeIf { it > 0 }?.let { ", $it more after it" } ?: "") + ".",
			PATHING_SOURCE,
		)
		PathingRequest(automated, next.goal, next.through).submit()
	}

	fun advanceCapture(budgetMillis: Double) {
		nextJourney?.world?.advance(budgetMillis)
	}

	fun onBlockChanged(pos: BlockPos) {
		nextJourney?.world?.onBlockChanged(pos)
	}

	fun onChunkEvent(x: Int, z: Int) {
		nextJourney?.world?.onChunkEvent(x, z)
	}
}
