package com.lambda.pathing.graph

internal class SlotEdges {
	var size = 0
		private set
	private var slots = IntArray(INITIAL)
	private var costs = DoubleArray(INITIAL)

	fun isEmpty(): Boolean = size == 0

	fun slot(index: Int): Int = slots[index]

	fun cost(index: Int): Double = costs[index]

	fun indexOf(slot: Int): Int {
		for (index in 0 until size) if (slots[index] == slot) return index
		return -1
	}

	fun costOf(slot: Int): Double {
		val index = indexOf(slot)
		return if (index < 0) Double.POSITIVE_INFINITY else costs[index]
	}

	fun append(slot: Int, cost: Double) {
		if (size == slots.size) {
			slots = slots.copyOf(size * 2)
			costs = costs.copyOf(size * 2)
		}
		slots[size] = slot
		costs[size] = cost
		size++
	}

	fun put(slot: Int, cost: Double) {
		val index = indexOf(slot)
		if (index < 0) append(slot, cost) else costs[index] = cost
	}

	fun remove(slot: Int): Boolean {
		val index = indexOf(slot)
		if (index < 0) return false
		System.arraycopy(slots, index + 1, slots, index, size - index - 1)
		System.arraycopy(costs, index + 1, costs, index, size - index - 1)
		size--
		return true
	}

	private companion object {
		const val INITIAL = 4
	}
}
