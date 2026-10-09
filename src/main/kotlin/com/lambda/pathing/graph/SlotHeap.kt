package com.lambda.pathing.graph

internal class SlotHeap(initialCapacity: Int = 64) {
	private var slots = IntArray(initialCapacity.coerceAtLeast(1))
	private var k1 = DoubleArray(slots.size)
	private var k2 = DoubleArray(slots.size)
	private var positions = IntArray(slots.size) { -1 }
	private var count = 0

	fun insert(slot: Int, first: Double, second: Double) {
		val existing = position(slot)
		if (existing >= 0) {
			updateAt(existing, first, second)
			return
		}
		ensureCapacity(count + 1)
		val newIndex = count++
		place(newIndex, slot, first, second)
		siftUp(newIndex)
	}

	fun remove(slot: Int): Boolean {
		val position = position(slot)
		if (position < 0) return false
		positions[slot] = -1
		removeAt(position)
		return true
	}

	fun top(): Int {
		if (count == 0) throw NoSuchElementException("Priority queue is empty")
		return slots[0]
	}

	fun topFirst(): Double = if (count == 0) Double.POSITIVE_INFINITY else k1[0]

	fun topSecond(): Double = if (count == 0) Double.POSITIVE_INFINITY else k2[0]

	fun topIsBelow(first: Double, second: Double): Boolean = compareKeys(topFirst(), topSecond(), first, second) < 0

	fun topIsAbove(first: Double, second: Double): Boolean = compareKeys(topFirst(), topSecond(), first, second) > 0

	operator fun contains(slot: Int): Boolean = position(slot) >= 0

	fun isEmpty(): Boolean = count == 0

	fun size(): Int = count

	fun slots(): IntArray = slots.copyOf(count)

	private fun position(slot: Int): Int = if (slot < positions.size) positions[slot] else -1

	private fun ensureCapacity(needed: Int) {
		if (needed <= slots.size) return
		val newSize = maxOf(needed, slots.size * 2)
		slots = slots.copyOf(newSize)
		k1 = k1.copyOf(newSize)
		k2 = k2.copyOf(newSize)
	}

	private fun updateAt(position: Int, first: Double, second: Double) {
		val cmp = compareKeys(first, second, k1[position], k2[position])
		if (cmp == 0) return
		k1[position] = first
		k2[position] = second
		if (cmp < 0) siftUp(position) else siftDown(position)
	}

	private fun removeAt(position: Int) {
		val lastIndex = --count
		if (position == lastIndex) return
		place(position, slots[lastIndex], k1[lastIndex], k2[lastIndex])
		val parent = (position - 1) ushr 1
		if (position > 0 && compareKeys(k1[position], k2[position], k1[parent], k2[parent]) < 0) siftUp(position)
		else siftDown(position)
	}

	private fun place(position: Int, slot: Int, first: Double, second: Double) {
		slots[position] = slot
		k1[position] = first
		k2[position] = second
		if (slot >= positions.size) {
			val grown = positions.size
			positions = positions.copyOf(maxOf(slot + 1, grown * 2))
			positions.fill(-1, grown, positions.size)
		}
		positions[slot] = position
	}

	private fun siftUp(start: Int) {
		var position = start
		val slot = slots[position]
		val first = k1[position]
		val second = k2[position]
		while (position > 0) {
			val parent = (position - 1) ushr 1
			if (compareKeys(first, second, k1[parent], k2[parent]) >= 0) break
			place(position, slots[parent], k1[parent], k2[parent])
			position = parent
		}
		place(position, slot, first, second)
	}

	private fun siftDown(start: Int) {
		var position = start
		val size = count
		val slot = slots[position]
		val first = k1[position]
		val second = k2[position]
		val half = size ushr 1
		while (position < half) {
			var child = (position shl 1) + 1
			val right = child + 1
			if (right < size && compareKeys(k1[right], k2[right], k1[child], k2[child]) < 0) {
				child = right
			}
			if (compareKeys(first, second, k1[child], k2[child]) <= 0) break
			place(position, slots[child], k1[child], k2[child])
			position = child
		}
		place(position, slot, first, second)
	}

	companion object {

		fun compareKeys(a1: Double, a2: Double, b1: Double, b2: Double): Int {
			val first = a1.compareTo(b1)
			return if (first != 0) first else a2.compareTo(b2)
		}
	}
}
