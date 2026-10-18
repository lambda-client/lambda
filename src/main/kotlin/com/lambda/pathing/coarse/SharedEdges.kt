package com.lambda.pathing.coarse

import com.lambda.pathing.core.CellBox
import com.lambda.pathing.core.Stance
import com.lambda.pathing.world.CoarseVoxelView
import it.unimi.dsi.fastutil.longs.LongOpenHashSet

class SharedEdges internal constructor(private val goals: List<Stance>) {
	private val members = ArrayList<CoarsePlanner>()
	private var cache: CoarseEdgeCache? = null
	internal var unknownDrops = LongOpenHashSet()
		private set

	internal fun cache(view: CoarseVoxelView, moves: SimpleMoveLibrary): CoarseEdgeCache =
		cache ?: CoarseEdgeCache(view.cursor(), moves, goals, unknownDrops).also { cache = it }

	internal fun join(planner: CoarsePlanner) {
		members += planner
	}

	internal fun leave(planner: CoarsePlanner) {
		members.remove(planner)
	}

	internal fun reset() {
		members.clear()
		cache = null
		unknownDrops = LongOpenHashSet()
	}

	internal fun terrainChanged(changed: List<CellBox>, maxStances: Int, arrivalsOnly: Boolean) {
		for (member in members) member.collectChanged(changed, arrivalsOnly)
		cache?.invalidate(changed, arrivalsOnly)
		for (member in members) member.afterChange(maxStances)
	}

	internal fun stancesInvalidated(stances: Iterable<Stance>, by: CoarsePlanner) {
		for (member in members) if (member !== by) member.markForSync(stances)
	}
}
