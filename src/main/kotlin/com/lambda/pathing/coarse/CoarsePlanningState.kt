package com.lambda.pathing.coarse

import com.lambda.pathing.actions.CoarseMoveCosts
import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.core.CellBox
import com.lambda.pathing.core.Stance
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.RememberedTerrain
import com.lambda.pathing.world.streaming.WorldEventBatch

private val DEFAULT_MOVE_COSTS: CoarseMoveCosts = CoarseMoveCosts.measured(transitionOverheadTicks = 1.0)

private val EVERYWHERE = ((Int.MIN_VALUE / 2)..(Int.MAX_VALUE / 2)).let { CellBox(it, it, it) }

internal class CoarsePlanningState(
	view: CoarseVoxelView,
	moveOptions: SimpleMoveOptions,
	start: Stance,
	val goal: Stance,
	private val memory: RememberedTerrain? = null,
) {
	private val moves = SimpleMoveLibrary.build(costs = DEFAULT_MOVE_COSTS, options = moveOptions)

	val planner = CoarsePlanner(view, moves, start, goal)

	fun repairFrom(start: Stance) {
		planner.updateStart(start)
	}

	fun applyEvents(batch: WorldEventBatch, maxStances: Int = Int.MAX_VALUE) {
		val (overRemembered, arrived) = batch.arrived.partition { memory?.remembers(it) == true }
		val changed = batch.changed + overRemembered
		if (changed.isNotEmpty()) planner.terrainChanged(changed, maxStances)
		if (arrived.isNotEmpty()) planner.terrainChanged(arrived, maxStances, arrivalsOnly = true)
	}

	fun forgetMemory(): Boolean {
		if (memory?.forget() != true) return false
		planner.terrainChanged(listOf(EVERYWHERE))
		return true
	}

	fun routePlan(snapshotRevision: Long, cancelled: () -> Boolean, maxExpansions: Int): CoarseRoutePlan? {
		var route = extract(snapshotRevision, cancelled, maxExpansions)
		repeat(MAX_RECALLS) {
			val end = route?.goal?.takeIf { it != goal && !cancelled() } ?: return route
			val recalled = memory?.recall(end)?.takeIf { it.isNotEmpty() } ?: return route
			planner.terrainChanged(recalled, arrivalsOnly = true)
			route = extract(snapshotRevision, cancelled, maxExpansions)
		}
		return route
	}

	private fun extract(snapshotRevision: Long, cancelled: () -> Boolean, maxExpansions: Int): CoarseRoutePlan? {
		planner.repair(maxExpansions = maxExpansions, cancelled = cancelled)
		return planner.routePlan(snapshotRevision, cancelled = cancelled)
			?: planner.resynchronizedRoutePlan(snapshotRevision, cancelled = cancelled, maxExpansions = maxExpansions)
	}

	private companion object {
		const val MAX_RECALLS = 16
	}
}
