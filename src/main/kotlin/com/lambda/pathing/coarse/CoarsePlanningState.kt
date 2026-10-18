package com.lambda.pathing.coarse

import com.lambda.pathing.actions.CoarseMoveCosts
import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.core.Stance
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.RememberedTerrain
import com.lambda.pathing.world.streaming.WorldEventBatch
import kotlin.math.abs

private val DEFAULT_MOVE_COSTS: CoarseMoveCosts = CoarseMoveCosts.measured()

internal class CoarsePlanningState(
	private val view: CoarseVoxelView,
	moveOptions: SimpleMoveOptions,
	start: Stance,
	goal: Stance,
	private val memory: RememberedTerrain? = null,
	private val alternatives: List<Stance> = emptyList(),
) {
	private val moves = libraryFor(moveOptions)

	var goal: Stance = goal
		private set

	var planner = CoarsePlanner(view, moves, start, goal, alternatives)
		private set

	fun retarget(goal: Stance, from: Stance, speed: SpeedClass) {
		this.goal = goal
		planner = CoarsePlanner(view, moves, from, goal, alternatives, reuse = planner).apply { updateStart(from, speed) }
	}

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
		planner = CoarsePlanner(view, moves, from, goal, alternatives).apply { updateStart(from, speed) }
		return true
	}

	fun recallMemory(route: CoarseRoutePlan, from: Stance): Boolean {
		val end = route.goal
		if (memory == null || end == goal || end in alternatives || maxOf(abs(end.x - from.x), abs(end.z - from.z)) > MEMORY_LOOKAHEAD_BLOCKS) return false
		val recalled = memory.recall(end)
		if (recalled.isEmpty()) return false
		planner.terrainChanged(recalled, arrivalsOnly = true)
		return true
	}

	fun routePlan(snapshotRevision: Long, cancelled: () -> Boolean, maxExpansions: Int): CoarseRoutePlan? {
		if (planner.repair(maxExpansions = maxExpansions, cancelled = cancelled).provenUnreachable) return null
		return planner.routePlan(snapshotRevision, cancelled = cancelled)
			?: planner.resynchronizedRoutePlan(snapshotRevision, cancelled = cancelled, maxExpansions = maxExpansions)
	}

	private companion object {
		const val MEMORY_LOOKAHEAD_BLOCKS = 640

		const val CACHED_LIBRARIES = 4

		val libraries = object : LinkedHashMap<SimpleMoveOptions, SimpleMoveLibrary>(CACHED_LIBRARIES, 0.75f, true) {
			override fun removeEldestEntry(eldest: MutableMap.MutableEntry<SimpleMoveOptions, SimpleMoveLibrary>): Boolean = size > CACHED_LIBRARIES
		}

		fun libraryFor(options: SimpleMoveOptions): SimpleMoveLibrary = synchronized(libraries) {
			libraries.getOrPut(options) { SimpleMoveLibrary.build(costs = DEFAULT_MOVE_COSTS, options = options) }
		}
	}
}
