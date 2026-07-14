/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.coarse

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
) {
    init {
        require(nodes.isNotEmpty()) { "A coarse route must contain its start" }
        require(edges.size == nodes.size - 1) { "Every consecutive node pair must have one edge" }
        require(edges.zip(nodes.zipWithNext()).all { (edge, pair) -> edge.from == pair.first && edge.to == pair.second }) {
            "Coarse route edges must connect consecutive nodes"
        }
    }

    val goal: Stance get() = nodes.last()

    /**
     * The first [nodeCount] nodes as a route in their own right.
     *
     * A trajectory can only be certified as far as the simulator can reach inside its
     * frame budget, so a long route is walked as a series of windows. Costs and
     * dependencies are recomputed from the surviving edges: a window must not carry
     * the tail's read set, or it would be invalidated by a block it never touches.
     */
    fun prefix(nodeCount: Int): CoarseRoutePlan {
        require(nodeCount in 2..nodes.size) { "A window must contain at least one edge" }
        if (nodeCount == nodes.size) return this

        val windowEdges = edges.take(nodeCount - 1)
        return copy(
            nodes = nodes.take(nodeCount),
            edges = windowEdges,
            lowerBoundTicks = windowEdges.sumOf { it.lowerBoundTicks },
            // Only the goal-reaching route can claim an exact cost to the goal.
            exactFromStart = false,
            dependencies = windowEdges.flatMapTo(HashSet()) { it.readSet },
        )
    }
}
