/*
 * Copyright 2025 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.pathing.dstar

import com.lambda.graphics.renderer.esp.builders.buildLine
import com.lambda.graphics.renderer.esp.builders.buildOutline
import com.lambda.graphics.renderer.esp.global.StaticESP
import com.lambda.pathing.PathingSettings
import com.lambda.util.world.FastVector
import com.lambda.util.world.toCenterVec3d
import net.minecraft.util.math.Box
import java.awt.Color
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * A 3D graph that uses FastVector (a Long) to represent 3D nodes.
 *
 * Runtime Complexity:
 * - successor(u): O(N), where N is the number of neighbors of node u, due to node initialization.
 * - predecessors(u): O(N), similar reasoning to successors(u).
 * - cost(u, v): O(1), constant time lookup after initialization.
 * - contains(u): O(1), hash map lookup.
 *
 * Space Complexity:
 * - O(V + E), where V = number of nodes (vertices) initialized and E = number of edges stored.
 * - Additional memory overhead is based on the dynamically expanding hash maps.
 */
class LazyGraph(
    val nodeInitializer: (FastVector) -> Map<FastVector, Double>
) {
    val successors = ConcurrentHashMap<FastVector, MutableMap<FastVector, Double>>()
    val predecessors = ConcurrentHashMap<FastVector, MutableMap<FastVector, Double>>()

    val nodes get() = successors.keys + predecessors.keys
    val size get() = nodes.size

    /** Initializes a node if not already initialized, then returns successors. */
    fun successors(u: FastVector): MutableMap<FastVector, Double> =
        successors.getOrPut(u) {
            nodeInitializer(u).onEach { (neighbor, cost) ->
                predecessors.getOrPut(neighbor) { hashMapOf() }[u] = cost
            }.toMutableMap()
        }

    /** Initializes predecessors by ensuring successors of neighboring nodes. */
    fun predecessors(u: FastVector): Map<FastVector, Double> {
        successors(u)
        return predecessors[u] ?: emptyMap()
    }

    fun removeNode(u: FastVector) {
        successors.remove(u)
        successors.values.forEach { it.remove(u) }
        predecessors.remove(u)
        predecessors.values.forEach { it.remove(u) }
    }

    private fun removeEdge(u: FastVector, v: FastVector) {
        successors[u]?.remove(v)
        predecessors[v]?.remove(u)
        if (successors[u]?.isEmpty() == true) {
            successors.remove(u)
        }
        if (predecessors[v]?.isEmpty() == true) {
            predecessors.remove(v)
        }
    }

    fun setCost(u: FastVector, v: FastVector, c: Double) {
        successors.getOrPut(u) { hashMapOf() }[v] = c
        predecessors.getOrPut(v) { hashMapOf() }[u] = c
    }

    fun clear() {
        successors.clear()
        predecessors.clear()
    }

    /**
     * Prunes the graph by removing unnecessary edges and nodes.
     * This helps keep the graph clean and efficient.
     * 
     * @param modifiedNodes A set of nodes that have been modified and need to be checked for pruning
     */
    fun prune(modifiedNodes: Set<FastVector> = emptySet()) {
        // Nodes to check for pruning
        val nodesToCheck = if (modifiedNodes.isEmpty()) {
            // If no modified nodes specified, check all nodes
            nodes.toSet()
        } else {
            // Only check modified nodes and their neighbors
            val nodesToProcess = mutableSetOf<FastVector>()
            nodesToProcess.addAll(modifiedNodes)

            // Add neighbors of modified nodes
            modifiedNodes.forEach { node ->
                if (node in this) {
                    nodesToProcess.addAll(neighbors(node))
                }
            }

            nodesToProcess
        }

        // First, remove all edges with infinite cost
        nodesToCheck.forEach { u ->
            val successorsToRemove = mutableListOf<FastVector>()

            // Find successors with infinite cost
            successors[u]?.forEach { (v, cost) ->
                if (cost.isInfinite()) {
                    successorsToRemove.add(v)
                }
            }

            // Remove the identified edges
            successorsToRemove.forEach { v ->
                removeEdge(u, v)
            }
        }

        // Then, remove nodes that only have infinite connections or no connections
        val nodesToRemove = mutableSetOf<FastVector>()

        nodesToCheck.forEach { node ->
            // Check if this node has any finite outgoing edges
            val hasFiniteOutgoing = successors[node]?.any { (_, cost) -> cost.isFinite() } ?: false

            // Check if this node has any finite incoming edges
            val hasFiniteIncoming = predecessors[node]?.any { (_, cost) -> cost.isFinite() } ?: false

            // If the node has no finite connections, mark it for removal
            if (!hasFiniteOutgoing && !hasFiniteIncoming) {
                nodesToRemove.add(node)
            }
        }

        // Remove nodes with only infinite connections
        nodesToRemove.forEach { removeNode(it) }
    }

    /**
     * Returns the successors of a node without initializing it if it doesn't exist.
     * This is useful for debugging and testing.
     */
    fun getSuccessorsWithoutInitializing(u: FastVector): Map<FastVector, Double> {
        return successors[u] ?: emptyMap()
    }

    /**
     * Returns the predecessors of a node without initializing it if it doesn't exist.
     * This is useful for debugging and testing.
     */
    fun getPredecessorsWithoutInitializing(u: FastVector): Map<FastVector, Double> {
        return predecessors[u] ?: emptyMap()
    }

    fun edges(u: FastVector) = successors(u).entries + predecessors(u).entries
    fun neighbors(u: FastVector): Set<FastVector> = successors(u).keys + predecessors(u).keys

    /** Returns the cost of the edge from u to v (or ∞ if none exists) */
    fun cost(u: FastVector, v: FastVector): Double = successors(u)[v] ?: Double.POSITIVE_INFINITY

    operator fun contains(u: FastVector): Boolean = nodes.contains(u)

    /**
     * Compares this graph with another graph for edge consistency.
     * Returns the percentage of edges that are consistent between the two graphs.
     * 
     * @param other The other graph to compare with
     * @return The percentage of consistent edges (0-100)
     */
    fun compareWith(other: LazyGraph): Double {
        val commonNodes = this.nodes.intersect(other.nodes)
        var consistentEdges = 0
        var totalEdges = 0

        commonNodes.forEach { node1 ->
            commonNodes.forEach { node2 ->
                if (node1 != node2) {
                    totalEdges++
                    val cost1 = this.cost(node1, node2)
                    val cost2 = other.cost(node1, node2)

                    // Check if costs are consistent
                    if (cost1.isInfinite() && cost2.isInfinite()) {
                        // Both infinite, they're consistent
                        consistentEdges++
                    } else if (cost1.isFinite() && cost2.isFinite()) {
                        // Both finite, check if they're close enough
                        if (abs(cost1 - cost2) < 0.001) {
                            consistentEdges++
                        }
                    }
                }
            }
        }

        return if (totalEdges > 0) {
            (consistentEdges.toDouble() / totalEdges) * 100
        } else {
            100.0
        }
    }

    fun render(renderer: StaticESP, config: PathingSettings) {
        if (!config.renderGraph) return
        if (config.renderSuccessors) successors.entries.take(config.maxRenderObjects).forEach { (origin, neighbors) ->
            neighbors.forEach { (neighbor, _) ->
                renderer.buildLine(origin.toCenterVec3d(), neighbor.toCenterVec3d(), Color.PINK)
            }
        }
        if (config.renderPredecessors) predecessors.entries.take(config.maxRenderObjects).forEach { (origin, neighbors) ->
            neighbors.forEach { (neighbor, _) ->
                renderer.buildLine(origin.toCenterVec3d(), neighbor.toCenterVec3d(), Color.PINK)
            }
        }
    }
}
