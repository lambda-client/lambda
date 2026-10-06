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

	var goal: Stance = initial.goal
		private set

	var goalPoint: HorizontalPoint = goal.center(environment)
		private set

	var index: Map<Stance, Int> = indexOf(initial)
		private set

	fun replace(next: CoarseRoutePlan) {
		plan = next
		goal = next.goal
		goalPoint = goal.center(environment)
		index = indexOf(next)
	}

	private fun indexOf(route: CoarseRoutePlan): Map<Stance, Int> =
		route.nodes.withIndex().associate { (index, node) -> node to index }
}
