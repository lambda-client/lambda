package com.lambda.pathing.graph

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import it.unimi.dsi.fastutil.longs.LongSet
import it.unimi.dsi.fastutil.longs.LongSets

/**
 * Lazily materialised adjacency over packed long nodes. Adjacency lists are [EdgeList]s
 * in insertion order (the search iterates them and tie-breaks on that order; see
 * docs/decisions/determinism.md). Providers write into an [EdgeSink]; non-finite costs are
 * validated and dropped, so the stored graph only ever holds finite edges.
 */
class LongLazyGraph(
    private val successorProvider: LongEdgeProvider,
    private val predecessorProvider: LongEdgeProvider = successorProvider,
) {
    private val successorEdges = Long2ObjectOpenHashMap<EdgeList>()
    private val predecessorEdges = Long2ObjectOpenHashMap<EdgeList>()
    private val initializedSuccessors = LongOpenHashSet()
    private val initializedPredecessors = LongOpenHashSet()
    private val knownNodes = LongOpenHashSet()
    private val knownNodesView: LongSet = LongSets.unmodifiable(knownNodes)

    val nodes: LongSet
        get() = knownNodesView

    val size: Int
        get() = knownNodes.size

    /** Live, read-only view; materialises the node's successors first. */
    fun successors(node: Long): EdgeList {
        ensureSuccessors(node)
        return knownSuccessors(node)
    }

    /** Live, read-only view; materialises the node's predecessors first. */
    fun predecessors(node: Long): EdgeList {
        ensurePredecessors(node)
        return knownPredecessors(node)
    }

    fun knownSuccessors(node: Long): EdgeList = successorEdges.get(node) ?: EdgeList.EMPTY
    fun knownPredecessors(node: Long): EdgeList = predecessorEdges.get(node) ?: EdgeList.EMPTY

    /** A fresh list from the provider with non-finite costs dropped; the graph is untouched. */
    fun generateSuccessors(node: Long): EdgeList {
        val out = EdgeList()
        successorProvider.edges(node, out)
        return out.dropNonFinite()
    }

    fun generatePredecessors(node: Long): EdgeList {
        val out = EdgeList()
        predecessorProvider.edges(node, out)
        return out.dropNonFinite()
    }

    fun markSuccessorsInitialized(node: Long) {
        initializedSuccessors.add(node)
        knownNodes.add(node)
    }

    fun markPredecessorsInitialized(node: Long) {
        initializedPredecessors.add(node)
        knownNodes.add(node)
    }

    fun setCost(from: Long, to: Long, cost: Double) {
        EdgeList.checkCost(cost)
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

    operator fun contains(node: Long): Boolean = knownNodes.contains(node)

    private fun ensureSuccessors(node: Long) {
        if (initializedSuccessors.contains(node)) return
        val generated = generateSuccessors(node)
        for (i in 0 until generated.size) putEdge(node, generated.node(i), generated.cost(i))
        initializedSuccessors.add(node)
        knownNodes.add(node)
    }

    private fun ensurePredecessors(node: Long) {
        if (initializedPredecessors.contains(node)) return
        val generated = generatePredecessors(node)
        for (i in 0 until generated.size) putEdge(generated.node(i), node, generated.cost(i))
        initializedPredecessors.add(node)
        knownNodes.add(node)
    }

    private fun putEdge(from: Long, to: Long, cost: Double) {
        knownNodes.add(from)
        knownNodes.add(to)
        edgesOf(successorEdges, from).add(to, cost)
        edgesOf(predecessorEdges, to).add(from, cost)
    }

    private fun edgesOf(map: Long2ObjectOpenHashMap<EdgeList>, node: Long): EdgeList {
        var list = map.get(node)
        if (list == null) {
            list = EdgeList()
            map.put(node, list)
        }
        return list
    }

    private fun removeEdge(from: Long, to: Long) {
        successorEdges.get(from)?.let { edges ->
            edges.remove(to)
            if (edges.isEmpty()) successorEdges.remove(from)
        }
        predecessorEdges.get(to)?.let { edges ->
            edges.remove(from)
            if (edges.isEmpty()) predecessorEdges.remove(to)
        }
        refreshKnownNode(from)
        refreshKnownNode(to)
    }

    private fun refreshKnownNode(node: Long) {
        if (!successorEdges.containsKey(node) && !predecessorEdges.containsKey(node) &&
            !initializedSuccessors.contains(node) && !initializedPredecessors.contains(node)
        ) {
            knownNodes.remove(node)
        }
    }
}
