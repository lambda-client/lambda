package com.lambda.pathing.graph

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap

internal class NodeTable {
	private val index = Long2IntOpenHashMap().apply { defaultReturnValue(-1) }
	private var keys = LongArray(INITIAL)
	private var g = DoubleArray(INITIAL)
	private var rhs = DoubleArray(INITIAL)
	private var flags = ByteArray(INITIAL)
	private var successors = arrayOfNulls<SlotEdges>(INITIAL)
	private var predecessors = arrayOfNulls<SlotEdges>(INITIAL)

	var size = 0
		private set

	var knownCount = 0
		private set

	fun find(node: Long): Int = index.get(node)

	fun slot(node: Long): Int {
		val found = index.get(node)
		if (found >= 0) return found
		if (size == keys.size) grow()
		keys[size] = node
		g[size] = INF
		rhs[size] = INF
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

	fun has(slot: Int, flag: Int): Boolean = flags[slot].toInt() and flag != 0

	fun mark(slot: Int, flag: Int) {
		if (flag and KNOWN != 0 && !has(slot, KNOWN)) knownCount++
		flags[slot] = (flags[slot].toInt() or flag).toByte()
	}

	fun forget(slot: Int) {
		if (has(slot, KNOWN)) knownCount--
		flags[slot] = (flags[slot].toInt() and KNOWN.inv()).toByte()
	}

	fun successors(slot: Int): SlotEdges? = successors[slot]

	fun predecessors(slot: Int): SlotEdges? = predecessors[slot]

	fun successorsOrCreate(slot: Int): SlotEdges = successors[slot] ?: SlotEdges().also { successors[slot] = it }

	fun predecessorsOrCreate(slot: Int): SlotEdges = predecessors[slot] ?: SlotEdges().also { predecessors[slot] = it }

	fun dropSuccessors(slot: Int) {
		successors[slot] = null
	}

	fun dropPredecessors(slot: Int) {
		predecessors[slot] = null
	}

	private fun grow() {
		val capacity = keys.size * 2
		keys = keys.copyOf(capacity)
		g = g.copyOf(capacity)
		rhs = rhs.copyOf(capacity)
		flags = flags.copyOf(capacity)
		successors = successors.copyOf(capacity)
		predecessors = predecessors.copyOf(capacity)
	}

	companion object {
		const val KNOWN = 1
		const val SUCCESSORS_INITIALIZED = 2
		const val PREDECESSORS_INITIALIZED = 4
		const val SUCCESSORS_SET = 8

		private const val INITIAL = 1024
		private const val INF = Double.POSITIVE_INFINITY
	}
}
