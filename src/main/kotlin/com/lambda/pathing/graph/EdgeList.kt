package com.lambda.pathing.graph

import it.unimi.dsi.fastutil.doubles.DoubleArrayList
import it.unimi.dsi.fastutil.longs.LongArrayList

interface EdgeSink {

	fun add(node: Long, cost: Double)

	fun addMin(node: Long, cost: Double)
}

fun interface LongEdgeProvider {
	fun edges(node: Long, sink: EdgeSink)
}

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
		for (i in targets.indices) if (elements[i] == node) return i
		return -1
	}

	operator fun contains(node: Long): Boolean = indexOf(node) >= 0

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

		val EMPTY = EdgeList(0)

		internal fun checkCost(cost: Double) {
			require(!cost.isNaN() && cost >= 0.0) {
				"D* Lite edge costs must be non-negative or +infinity: $cost"
			}
		}
	}
}
