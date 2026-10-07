package com.lambda.pathing.search

import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.core.HorizontalPoint
import com.lambda.pathing.core.Stance
import com.lambda.pathing.world.center
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment

internal class RouteState(
	initial: CoarseRoutePlan,
	private val environment: SnapshotSimulationEnvironment,
) {
	var plan: CoarseRoutePlan = initial
		private set

	val goal: Stance get() = plan.goal

	var goalPoint: HorizontalPoint = goal.center(environment)
		private set

	var index: Map<Stance, Int> = indexOf(initial)
		private set

	var revision: Long = initial.snapshotRevision
		private set

	private val touchedLegs = ArrayList<LegTouch>()

	val touches: List<LegTouch> get() = touchedLegs

	fun advanceRevision(synced: Long) {
		revision = maxOf(revision, synced)
	}

	fun switchLeg(next: CoarseRoutePlan, frame: Int) {
		touchedLegs += LegTouch(frame, goal)
		replace(next)
	}

	fun replace(next: CoarseRoutePlan) {
		plan = next
		goalPoint = goal.center(environment)
		index = indexOf(next)
	}

	private fun indexOf(route: CoarseRoutePlan): Map<Stance, Int> =
		route.nodes.withIndex().associate { (index, node) -> node to index }
}
