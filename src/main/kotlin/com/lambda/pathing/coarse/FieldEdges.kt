package com.lambda.pathing.coarse

import com.lambda.pathing.actions.CoarseEdge
import com.lambda.pathing.core.Stance
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.streaming.WorldEventBatch

class FieldEdges(private val view: CoarseVoxelView, private val moves: SimpleMoveLibrary, goals: List<Stance> = emptyList()) {
	private val cache = CoarseEdgeCache(view.cursor(), moves, goals)

	fun field(goal: Stance, labels: FieldLabels, revision: Long): CoarseValueField =
		CoarseValueField(view, moves, goal, labels::label, ::edgesFrom) { revision }

	fun invalidate(batch: WorldEventBatch) {
		cache.invalidate(batch.changed, arrivalsOnly = false)
		cache.invalidate(batch.arrived, arrivalsOnly = true)
	}

	private fun edgesFrom(stance: Stance): List<CoarseEdge> = cache.edgesFrom(stance)
}
