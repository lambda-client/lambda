package com.lambda.pathing.graph

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import it.unimi.dsi.fastutil.longs.LongSet
import it.unimi.dsi.fastutil.longs.LongSets

class LongLazyGraph(
	private val successorProvider: LongEdgeProvider,
	private val predecessorProvider: LongEdgeProvider = successorProvider,
) {
	private val successorEdges = Long2ObjectOpenHashMap<EdgeList>()
	private val predecessorEdges = Long2ObjectOpenHashMap<EdgeList>()
	private val initializedSuccessors = LongOpenHashSet()
	private val initializedPredecessors = LongOpenHashSet()
	private val knownNodes = LongOpenHashSet()

	val nodes: LongSet = LongSets.unmodifiable(knownNodes)

	val size: Int
		get() = knownNodes.size

	fun successors(node: Long): EdgeList {
		ensureSuccessors(node)
		return knownSuccessors(node)
	}

	fun predecessors(node: Long): EdgeList {
		ensurePredecessors(node)
		return knownPredecessors(node)
	}

	fun knownSuccessors(node: Long): EdgeList = successorEdges.get(node) ?: EdgeList.EMPTY
	fun knownPredecessors(node: Long): EdgeList = predecessorEdges.get(node) ?: EdgeList.EMPTY

	fun generateSuccessors(node: Long): EdgeList = generate(successorProvider, node)

	fun generatePredecessors(node: Long): EdgeList = generate(predecessorProvider, node)

	private fun generate(provider: LongEdgeProvider, node: Long): EdgeList =
		EdgeList().also { provider.edges(node, it) }.dropNonFinite()

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

	operator fun contains(node: Long): Boolean = knownNodes.contains(node)

	private fun ensureSuccessors(node: Long) {
		if (initializedSuccessors.contains(node)) return
		val generated = generateSuccessors(node)
		for (i in 0 until generated.size) addUnknownEdge(node, generated.node(i), generated.cost(i))
		markSuccessorsInitialized(node)
	}

	private fun ensurePredecessors(node: Long) {
		if (initializedPredecessors.contains(node)) return
		val generated = generatePredecessors(node)
		for (i in 0 until generated.size) addUnknownEdge(generated.node(i), node, generated.cost(i))
		markPredecessorsInitialized(node)
	}

	private fun addUnknownEdge(from: Long, to: Long, cost: Double) {
		if (!edgesOf(successorEdges, from).addIfAbsent(to, cost)) return
		knownNodes.add(from)
		knownNodes.add(to)
		edgesOf(predecessorEdges, to).add(from, cost)
	}

	private fun putEdge(from: Long, to: Long, cost: Double) {
		knownNodes.add(from)
		knownNodes.add(to)
		edgesOf(successorEdges, from).add(to, cost)
		edgesOf(predecessorEdges, to).add(from, cost)
	}

	private fun edgesOf(map: Long2ObjectOpenHashMap<EdgeList>, node: Long): EdgeList =
		map.get(node) ?: EdgeList().also { map.put(node, it) }

	private fun removeEdge(from: Long, to: Long) {
		detach(successorEdges, from, to)
		detach(predecessorEdges, to, from)
		refreshKnownNode(from)
		refreshKnownNode(to)
	}

	private fun detach(map: Long2ObjectOpenHashMap<EdgeList>, owner: Long, other: Long) {
		val edges = map.get(owner) ?: return
		edges.remove(other)
		if (edges.isEmpty()) map.remove(owner)
	}

	private fun refreshKnownNode(node: Long) {
		if (!successorEdges.containsKey(node) && !predecessorEdges.containsKey(node) &&
			!initializedSuccessors.contains(node) && !initializedPredecessors.contains(node)
		) {
			knownNodes.remove(node)
		}
	}
}
