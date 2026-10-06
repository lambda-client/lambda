package com.lambda.pathing.coarse

import com.lambda.pathing.actions.CoarseEdge
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.VoxelPos

data class CoarseRoutePlan(
	val snapshotRevision: Long,
	val nodes: List<Stance>,
	val edges: List<CoarseEdge>,
	val lowerBoundTicks: Double,
	val dependencies: Set<VoxelPos>,
) {
	init {
		require(nodes.isNotEmpty()) { "A coarse route must contain its start" }
		require(edges.size == nodes.size - 1) { "Every consecutive node pair must have one edge" }
		require(edges.zip(nodes.zipWithNext()).all { (edge, pair) -> edge.from == pair.first && edge.to == pair.second }) {
			"Coarse route edges must connect consecutive nodes"
		}
	}

	val goal: Stance get() = nodes.last()
}
