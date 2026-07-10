/*
 * Copyright 2026 Lambda
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

package com.lambda.pathing.core

/**
 * Sparse directed graph that generates adjacency on demand.
 *
 * [successorProvider] returns outgoing edges from a node.
 * [predecessorProvider] returns incoming edges to a node. If omitted, the graph
 * assumes reversible connectivity and reuses [successorProvider].
 */
class LazyGraph<N>(
    private val successorProvider: (N) -> Map<N, Double>,
    private val predecessorProvider: (N) -> Map<N, Double> = successorProvider,
) {
    private val successorEdges = HashMap<N, HashMap<N, Double>>()
    private val predecessorEdges = HashMap<N, HashMap<N, Double>>()
    private val initializedSuccessors = HashSet<N>()
    private val initializedPredecessors = HashSet<N>()

    val nodes: Set<N>
        get() = buildSet {
            addAll(successorEdges.keys)
            addAll(predecessorEdges.keys)
            // A generated empty adjacency is still planner knowledge. Keeping
            // these nodes visible is essential for world-change invalidation:
            // an initialized dead end may gain its first edge later.
            addAll(initializedSuccessors)
            addAll(initializedPredecessors)
        }

    val size: Int
        get() = nodes.size

    fun successors(node: N): Map<N, Double> {
        ensureSuccessors(node)
        return knownSuccessors(node)
    }

    fun predecessors(node: N): Map<N, Double> {
        ensurePredecessors(node)
        return knownPredecessors(node)
    }

    fun knownSuccessors(node: N): Map<N, Double> = successorEdges[node] ?: emptyMap()
    fun knownPredecessors(node: N): Map<N, Double> = predecessorEdges[node] ?: emptyMap()

    fun generateSuccessors(node: N): Map<N, Double> = successorProvider(node).filterFiniteCosts()
    fun generatePredecessors(node: N): Map<N, Double> = predecessorProvider(node).filterFiniteCosts()

    fun markSuccessorsInitialized(node: N) {
        initializedSuccessors += node
    }

    fun cost(from: N, to: N): Double {
        ensureSuccessors(from)
        return successorEdges[from]?.get(to) ?: Double.POSITIVE_INFINITY
    }

    fun setCost(from: N, to: N, cost: Double) {
        if (cost.isFinite()) putEdge(from, to, cost)
        else removeEdge(from, to)
    }

    fun removeNode(node: N) {
        successorEdges.remove(node)?.keys?.forEach { predecessorEdges[it]?.remove(node) }
        predecessorEdges.remove(node)?.keys?.forEach { successorEdges[it]?.remove(node) }
        initializedSuccessors.remove(node)
        initializedPredecessors.remove(node)
    }

    fun clear() {
        successorEdges.clear()
        predecessorEdges.clear()
        initializedSuccessors.clear()
        initializedPredecessors.clear()
    }

    operator fun contains(node: N) =
        node in successorEdges || node in predecessorEdges ||
            node in initializedSuccessors || node in initializedPredecessors

    private fun ensureSuccessors(node: N) {
        if (!initializedSuccessors.add(node)) return
        generateSuccessors(node).forEach { (successor, cost) -> putEdge(node, successor, cost) }
    }

    private fun ensurePredecessors(node: N) {
        if (!initializedPredecessors.add(node)) return
        generatePredecessors(node).forEach { (predecessor, cost) -> putEdge(predecessor, node, cost) }
    }

    private fun putEdge(from: N, to: N, cost: Double) {
        successorEdges.getOrPut(from) { HashMap() }[to] = cost
        predecessorEdges.getOrPut(to) { HashMap() }[from] = cost
    }

    private fun removeEdge(from: N, to: N) {
        successorEdges[from]?.let { edges ->
            edges.remove(to)
            if (edges.isEmpty()) successorEdges.remove(from)
        }
        predecessorEdges[to]?.let { edges ->
            edges.remove(from)
            if (edges.isEmpty()) predecessorEdges.remove(to)
        }
    }
}

private fun <N> Map<N, Double>.filterFiniteCosts() = filterValues { it.isFinite() }
