package com.lambda.pathing.coarse

import com.lambda.pathing.actions.CoarseMoveCosts
import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.core.CellBox
import com.lambda.pathing.core.Stance
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.RememberedTerrain
import com.lambda.pathing.world.streaming.InterestTier
import com.lambda.pathing.world.streaming.PathingWorld
import com.lambda.pathing.world.streaming.WorldEventBatch
import net.minecraft.util.math.ChunkSectionPos

private val DEFAULT_MOVE_COSTS: CoarseMoveCosts = CoarseMoveCosts.measured(transitionOverheadTicks = 1.0)

private val EVERYWHERE = ((Int.MIN_VALUE / 2)..(Int.MAX_VALUE / 2)).let { CellBox(it, it, it) }

internal class CoarsePlanningState(
	view: CoarseVoxelView,
	moveOptions: SimpleMoveOptions,
	start: Stance,
	val goal: Stance,
	frontierSweepBudget: Int = FrontierAnchors.SWEEP_MAX_NODES,
	capturable: (Int, Int) -> Boolean = FrontierAnchors.NOTHING_CAPTURABLE,
	private val memory: RememberedTerrain? = null,
) {
	private val moves = SimpleMoveLibrary.build(costs = DEFAULT_MOVE_COSTS, options = moveOptions)

	private val captureLagSections = HashSet<Long>()

	val planner = CoarsePlanner(
		view, moves, start, goal,
		sweepBudget = frontierSweepBudget,
		capturable = capturable,
		onCaptureLag = { x, y, z -> captureLagSections += ChunkSectionPos.asLong(x shr 4, y shr 4, z shr 4) },
	)

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

	fun demandCaptureLag(world: PathingWorld, tier: InterestTier = InterestTier.DEMAND) {
		if (captureLagSections.isEmpty()) return
		world.interest(ArrayList(captureLagSections), tier)
		captureLagSections.clear()
	}

	fun routePlan(snapshotRevision: Long, cancelled: () -> Boolean, maxExpansions: Int): CoarseRoutePlan? =
		planner.routePlan(snapshotRevision, cancelled = cancelled)
			?: planner.resynchronizedRoutePlan(snapshotRevision, cancelled = cancelled, maxExpansions = maxExpansions)
			?: run {
				if (planner.discoverReachableFrontier(cancelled, maxExpansions)) {
					planner.routePlan(snapshotRevision, cancelled = cancelled)
				} else null
			}
}
