/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.coarse

import com.lambda.pathing.core.TailCost
import com.lambda.pathing.world.VoxelPos

/** Immutable worker-to-trajectory boundary; it contains no live D* state. */
data class CoarseRoutePlan(
    val snapshotRevision: Long,
    val routeVersion: Long,
    val nodes: List<Stance>,
    val edges: List<CoarseEdge>,
    val lowerBoundTicks: Double,
    val exactFromStart: Boolean,
    val dependencies: Set<VoxelPos>,
    /**
     * Correctness-bearing cost-to-go answers for [nodes], frozen when this route is
     * published. An intermediate D* label is never smuggled across this boundary as an
     * exact value: non-active nodes carry [TailCost.Bounds].
     */
    val tailCosts: List<TailCost>,
    /** Caps used to extend the stance heuristic to continuous simulated checkpoints. */
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
