package com.lambda.pathing.graph

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap

internal class NodeTable {
	private val index = Long2IntOpenHashMap().apply { defaultReturnValue(-1) }
	private var keys = LongArray(INITIAL)
	private var g = DoubleArray(INITIAL)
	private var rhs = DoubleArray(INITIAL)
	private var best = IntArray(INITIAL)

	var size = 0
		private set

	fun find(node: Long): Int = index.get(node)

	fun slot(node: Long): Int {
		val found = index.get(node)
		if (found >= 0) return found
		if (size == keys.size) grow()
		keys[size] = node
		g[size] = INF
		rhs[size] = INF
		best[size] = NONE
		index.put(node, size)
		return size++
	}

	fun key(slot: Int): Long = keys[slot]

	fun g(slot: Int): Double = g[slot]

	fun rhs(slot: Int): Double = rhs[slot]

	fun setG(slot: Int, value: Double) {
		g[slot] = value
	}

	fun setRhs(slot: Int, value: Double) {
		rhs[slot] = value
	}

	fun best(slot: Int): Int = best[slot]

	fun setBest(slot: Int, successor: Int) {
		best[slot] = successor
	}

	private fun grow() {
		val capacity = keys.size * 2
		keys = keys.copyOf(capacity)
		g = g.copyOf(capacity)
		rhs = rhs.copyOf(capacity)
		best = best.copyOf(capacity)
	}

	companion object {
		private const val INITIAL = 1024
		private const val INF = Double.POSITIVE_INFINITY
		const val NONE = -1
	}
}
