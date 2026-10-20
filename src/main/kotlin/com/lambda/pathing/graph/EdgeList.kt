package com.lambda.pathing.graph

import it.unimi.dsi.fastutil.doubles.DoubleArrayList
import it.unimi.dsi.fastutil.longs.LongArrayList

fun interface LongEdgeProvider {
	fun edges(node: Long, sink: EdgeList)
}

fun interface FilteredEdgeProvider {
	fun edges(node: Long, sink: EdgeList, wanted: java.util.function.LongPredicate)
}

fun interface ValuedEdgeProvider {
	fun edges(node: Long, sink: EdgeList, value: java.util.function.LongToDoubleFunction)
}

fun interface EdgeRelevance {
	fun relevant(node: Long, lowerBound: Double): Boolean
}

fun interface RelevantEdgeProvider {
	fun edges(node: Long, sink: EdgeList, relevance: EdgeRelevance)
}

class EdgeList(initialCapacity: Int = 16) {
	private val targets = LongArrayList(initialCapacity)
	private val costs = DoubleArrayList(initialCapacity)

	val size: Int get() = targets.size

	fun isEmpty(): Boolean = targets.isEmpty

	fun clear() {
		targets.clear()
		costs.clear()
	}

	fun node(index: Int): Long = targets.getLong(index)

	fun cost(index: Int): Double = costs.getDouble(index)

	fun indexOf(node: Long): Int {
		val elements = targets.elements()
		for (i in targets.indices) if (elements[i] == node) return i
		return -1
	}

	operator fun contains(node: Long): Boolean = indexOf(node) >= 0

	fun costOf(node: Long): Double {
		val index = indexOf(node)
		return if (index < 0) Double.POSITIVE_INFINITY else costs.getDouble(index)
	}

	fun add(node: Long, cost: Double) = put(node, cost, keepLower = false)

	fun addMin(node: Long, cost: Double) = put(node, cost, keepLower = true)

	fun append(node: Long, cost: Double) {
		checkCost(cost)
		targets.add(node)
		costs.add(cost)
	}

	private fun put(node: Long, cost: Double, keepLower: Boolean) {
		checkCost(cost)
		val index = indexOf(node)
		if (index < 0) {
			targets.add(node)
			costs.add(cost)
		} else if (!keepLower || cost < costs.getDouble(index)) {
			costs.set(index, cost)
		}
	}

	fun remove(node: Long): Boolean {
		val index = indexOf(node)
		if (index < 0) return false
		targets.removeLong(index)
		costs.removeDouble(index)
		return true
	}

	fun dropNonFinite(): EdgeList {
		var write = 0
		for (read in targets.indices) {
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

	companion object {

		val EMPTY = EdgeList(0)

		internal fun checkCost(cost: Double) {
			require(!cost.isNaN() && cost >= 0.0) {
				"D* Lite edge costs must be non-negative or +infinity: $cost"
			}
		}
	}
}
