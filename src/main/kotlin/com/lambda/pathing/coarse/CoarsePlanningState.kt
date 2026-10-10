package com.lambda.pathing.coarse

import com.lambda.pathing.actions.CoarseMoveCosts
import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.core.Stance
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.RememberedTerrain
import com.lambda.pathing.world.streaming.WorldEventBatch
import kotlin.math.abs

private val DEFAULT_MOVE_COSTS: CoarseMoveCosts = CoarseMoveCosts.measured(transitionOverheadTicks = 1.0)

internal class CoarsePlanningState(
	private val view: CoarseVoxelView,
	moveOptions: SimpleMoveOptions,
	start: Stance,
	val goal: Stance,
	private val memory: RememberedTerrain? = null,
) {
	private val moves = SimpleMoveLibrary.build(costs = DEFAULT_MOVE_COSTS, options = moveOptions)

	var planner = CoarsePlanner(view, moves, start, goal)
		private set

	fun repairFrom(start: Stance) {
		planner.updateStart(start)
	}

	fun applyEvents(batch: WorldEventBatch, maxStances: Int = Int.MAX_VALUE) {
		val (overRemembered, arrived) = batch.arrived.partition { memory?.remembers(it) == true }
		val changed = batch.changed + overRemembered
		if (changed.isNotEmpty()) planner.terrainChanged(changed, maxStances)
		if (arrived.isNotEmpty()) planner.terrainChanged(arrived, maxStances, arrivalsOnly = true)
	}

	fun forgetMemory(from: Stance, speed: SpeedClass = SpeedClass.STOPPED): Boolean {
		if (memory?.forget() != true) return false
		planner = CoarsePlanner(view, moves, from, goal).apply { updateStart(from, speed) }
		return true
	}

	fun recallMemory(route: CoarseRoutePlan, from: Stance): Boolean {
		val end = route.goal
		if (memory == null || end == goal || maxOf(abs(end.x - from.x), abs(end.z - from.z)) > MEMORY_LOOKAHEAD_BLOCKS) return false
		val recalled = memory.recall(end)
		if (recalled.isEmpty()) return false
		planner.terrainChanged(recalled, arrivalsOnly = true)
		return true
	}

	fun routePlan(snapshotRevision: Long, cancelled: () -> Boolean, maxExpansions: Int): CoarseRoutePlan? {
		planner.repair(maxExpansions = maxExpansions, cancelled = cancelled)
		return planner.routePlan(snapshotRevision, cancelled = cancelled)
			?: planner.resynchronizedRoutePlan(snapshotRevision, cancelled = cancelled, maxExpansions = maxExpansions)
	}

	private companion object {
		const val MEMORY_LOOKAHEAD_BLOCKS = 640
	}
}
