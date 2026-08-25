package com.lambda.pathing.graph

import java.util.Collections

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

    fun generateSuccessors(node: N): Map<N, Double> = successorProvider(node).filterUsableCosts()
    fun generatePredecessors(node: N): Map<N, Double> = predecessorProvider(node).filterUsableCosts()

    fun markSuccessorsInitialized(node: N) {
        initializedSuccessors += node
        knownNodes += node
    }

    fun markPredecessorsInitialized(node: N) {
        initializedPredecessors += node
        knownNodes += node
    }

    fun cost(from: N, to: N): Double {
        ensureSuccessors(from)
        return successorEdges[from]?.get(to) ?: Double.POSITIVE_INFINITY
    }

    fun setCost(from: N, to: N, cost: Double) {
        require(!cost.isNaN() && cost >= 0.0) { "D* Lite edge costs must be non-negative or +infinity: $cost" }
        if (cost.isFinite()) putEdge(from, to, cost)
        else removeEdge(from, to)
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
        if (node in initializedSuccessors) return

        val generated = generateSuccessors(node)
        generated.forEach { (successor, cost) -> putEdge(node, successor, cost) }
        initializedSuccessors += node
        knownNodes += node
    }

    private fun ensurePredecessors(node: N) {
        if (node in initializedPredecessors) return
        val generated = generatePredecessors(node)
        generated.forEach { (predecessor, cost) -> putEdge(predecessor, node, cost) }
        initializedPredecessors += node
        knownNodes += node
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

private fun <N> Map<N, Double>.filterUsableCosts(): Map<N, Double> {
    var filtered: HashMap<N, Double>? = null
    for ((node, cost) in this) {
        require(!cost.isNaN() && cost >= 0.0) {
            "D* Lite edge costs must be non-negative or +infinity: $cost"
        }
        if (!cost.isFinite()) {
            val target = filtered ?: HashMap(this).also { filtered = it }
            target.remove(node)
        }
    }
    return filtered ?: this
}
