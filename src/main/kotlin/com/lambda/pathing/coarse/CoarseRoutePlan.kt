package com.lambda.pathing.coarse

import com.lambda.pathing.actions.CoarseEdge
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.VoxelPos
import com.lambda.pathing.graph.TailCost

data class CoarseRoutePlan(
	val snapshotRevision: Long,
	val routeVersion: Long,
	val nodes: List<Stance>,
	val edges: List<CoarseEdge>,
	val lowerBoundTicks: Double,
	val exactFromStart: Boolean,
	val dependencies: Set<VoxelPos>,
	val tailCosts: List<TailCost>,
	val heuristicCaps: SimpleMoveLibrary.HeuristicCaps,
) {
	init {
		require(nodes.isNotEmpty()) { "A coarse route must contain its start" }
		require(edges.size == nodes.size - 1) { "Every consecutive node pair must have one edge" }
		require(edges.zip(nodes.zipWithNext()).all { (edge, pair) -> edge.from == pair.first && edge.to == pair.second }) {
			"Coarse route edges must connect consecutive nodes"
		}
		require(tailCosts.size == nodes.size) {
			"A published tail cost must correspond to every route node"
		}
	}

	val goal: Stance get() = nodes.last()
}
