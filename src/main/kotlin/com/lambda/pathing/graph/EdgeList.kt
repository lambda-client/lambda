package com.lambda.pathing.graph

import it.unimi.dsi.fastutil.doubles.DoubleArrayList
import it.unimi.dsi.fastutil.longs.LongArrayList

/** Where an edge provider writes its edges; no map allocation per expansion. */
interface EdgeSink {
	/** Put semantics: an existing target keeps its position and takes the new cost. */
	fun add(node: Long, cost: Double)

	/** Merge-min semantics: an existing target keeps its position and the smaller cost. */
	fun addMin(node: Long, cost: Double)
}

/** An edge provider over packed long nodes; costs are validated by the sink. */
fun interface LongEdgeProvider {
	fun edges(node: Long, sink: EdgeSink)
}

/**
 * Parallel target/cost arrays kept in INSERTION ORDER. The D* successor iteration and its
 * tie-breaks depend on this order being JVM-stable; see docs/decisions/determinism.md.
 * Lookups are linear: a node's adjacency is a few dozen edges at most.
 *
 * Graph callers receive live lists and must treat them as read-only.
 */
class EdgeList(initialCapacity: Int = 16) : EdgeSink {
	private val targets = LongArrayList(initialCapacity)
	private val costs = DoubleArrayList(initialCapacity)

	val size: Int get() = targets.size

	fun isEmpty(): Boolean = targets.isEmpty

	fun isNotEmpty(): Boolean = !targets.isEmpty

	fun node(index: Int): Long = targets.getLong(index)

	fun cost(index: Int): Double = costs.getDouble(index)

	fun indexOf(node: Long): Int {
		val elements = targets.elements()
		for (i in 0 until targets.size) if (elements[i] == node) return i
		return -1
	}

	operator fun contains(node: Long): Boolean = indexOf(node) >= 0

	/** The stored cost, or +infinity when [node] is not a target. */
	fun costOf(node: Long): Double {
		val index = indexOf(node)
		return if (index < 0) Double.POSITIVE_INFINITY else costs.getDouble(index)
	}

	override fun add(node: Long, cost: Double) {
		checkCost(cost)
		val index = indexOf(node)
		if (index >= 0) costs.set(index, cost)
		else {
			targets.add(node)
			costs.add(cost)
		}
	}

	override fun addMin(node: Long, cost: Double) {
		checkCost(cost)
		val index = indexOf(node)
		if (index >= 0) {
			if (cost < costs.getDouble(index)) costs.set(index, cost)
		} else {
			targets.add(node)
			costs.add(cost)
		}
	}

	/** Removes [node], shifting later edges down so their order is preserved. */
	fun remove(node: Long): Boolean {
		val index = indexOf(node)
		if (index < 0) return false
		targets.removeLong(index)
		costs.removeDouble(index)
		return true
	}

	/** Drops every non-finite edge in place, preserving the order of the rest. */
	fun dropNonFinite(): EdgeList {
		var write = 0
		for (read in 0 until targets.size) {
			val cost = costs.getDouble(read)
			if (!cost.isFinite()) continue
			if (write != read) {
				targets.set(write, targets.getLong(read))
				costs.set(write, cost)
			}
			write++
		}
		targets.size(write)
		costs.size(write)
		return this
	}

	fun copy(): EdgeList {
		val out = EdgeList(maxOf(size, 1))
		out.targets.addAll(targets)
		out.costs.addAll(costs)
		return out
	}

	fun clear() {
		targets.clear()
		costs.clear()
	}

	fun toMap(): Map<Long, Double> {
		val out = LinkedHashMap<Long, Double>(size * 2)
		for (i in 0 until size) out[node(i)] = cost(i)
		return out
	}

	override fun toString(): String = (0 until size).joinToString(", ", "[", "]") { "${node(it)}=${cost(it)}" }

	companion object {
		/** Shared read-only empty list returned for unknown nodes. Never mutate. */
		val EMPTY = EdgeList(0)

		internal fun checkCost(cost: Double) {
			require(!cost.isNaN() && cost >= 0.0) {
				"D* Lite edge costs must be non-negative or +infinity: $cost"
			}
		}
	}
}
