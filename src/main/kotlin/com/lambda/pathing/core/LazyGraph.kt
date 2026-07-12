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

import java.util.Collections

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
    private val knownNodes = HashSet<N>()
    private val knownNodesView: Set<N> = Collections.unmodifiableSet(knownNodes)

    val nodes: Set<N>
        get() = knownNodesView

    val size: Int
        get() = knownNodes.size

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
        knownNodes += node
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
        successorEdges.remove(node)?.keys?.forEach { successor ->
            predecessorEdges[successor]?.let { edges ->
                edges.remove(node)
                if (edges.isEmpty()) predecessorEdges.remove(successor)
            }
            refreshKnownNode(successor)
        }
        predecessorEdges.remove(node)?.keys?.forEach { predecessor ->
            successorEdges[predecessor]?.let { edges ->
                edges.remove(node)
                if (edges.isEmpty()) successorEdges.remove(predecessor)
            }
            refreshKnownNode(predecessor)
        }
        initializedSuccessors.remove(node)
        initializedPredecessors.remove(node)
        knownNodes.remove(node)
    }

    fun clear() {
        successorEdges.clear()
        predecessorEdges.clear()
        initializedSuccessors.clear()
        initializedPredecessors.clear()
        knownNodes.clear()
    }

    operator fun contains(node: N) = node in knownNodes

    private fun ensureSuccessors(node: N) {
        if (!initializedSuccessors.add(node)) return
        knownNodes += node
        generateSuccessors(node).forEach { (successor, cost) -> putEdge(node, successor, cost) }
    }

    private fun ensurePredecessors(node: N) {
        if (!initializedPredecessors.add(node)) return
        knownNodes += node
        generatePredecessors(node).forEach { (predecessor, cost) -> putEdge(predecessor, node, cost) }
    }

    private fun putEdge(from: N, to: N, cost: Double) {
        knownNodes += from
        knownNodes += to
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
        refreshKnownNode(from)
        refreshKnownNode(to)
    }

    private fun refreshKnownNode(node: N) {
        if (node !in successorEdges && node !in predecessorEdges &&
            node !in initializedSuccessors && node !in initializedPredecessors
        ) {
            knownNodes.remove(node)
        }
    }
}

/** Keep the provider's map allocation when it is already valid (the common path). */
private fun <N> Map<N, Double>.filterFiniteCosts(): Map<N, Double> {
    var filtered: HashMap<N, Double>? = null
    for ((node, cost) in this) {
        if (!cost.isFinite()) {
            val target = filtered ?: HashMap(this).also { filtered = it }
            target.remove(node)
        }
    }
    return filtered ?: this
}
