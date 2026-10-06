package com.lambda.pathing.graph

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap

class LongIndexedHeap(initialCapacity: Int = 64) {
	private var nodes = LongArray(initialCapacity.coerceAtLeast(1))
	private var k1 = DoubleArray(nodes.size)
	private var k2 = DoubleArray(nodes.size)
	private var count = 0
	private val index = Long2IntOpenHashMap(initialCapacity).apply { defaultReturnValue(-1) }

	fun insert(node: Long, first: Double, second: Double) {
		val existing = index.get(node)
		if (existing >= 0) {
			updateAt(existing, first, second)
			return
		}
		ensureCapacity(count + 1)
		val newIndex = count++
		place(newIndex, node, first, second)
		siftUp(newIndex)
	}

	fun remove(node: Long): Boolean {
		val position = index.remove(node)
		if (position < 0) return false
		removeAt(position)
		return true
	}

	fun top(): Long {
		if (count == 0) throw NoSuchElementException("Priority queue is empty")
		return nodes[0]
	}

	fun topFirst(): Double = if (count == 0) Double.POSITIVE_INFINITY else k1[0]

	fun topSecond(): Double = if (count == 0) Double.POSITIVE_INFINITY else k2[0]

	fun topIsBelow(first: Double, second: Double): Boolean = compareKeys(topFirst(), topSecond(), first, second) < 0

	fun topIsAbove(first: Double, second: Double): Boolean = compareKeys(topFirst(), topSecond(), first, second) > 0

	operator fun contains(node: Long): Boolean = index.containsKey(node)

	fun isEmpty(): Boolean = count == 0

	fun size(): Int = count

	private fun ensureCapacity(needed: Int) {
		if (needed <= nodes.size) return
		val newSize = maxOf(needed, nodes.size * 2)
		nodes = nodes.copyOf(newSize)
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
		place(position, nodes[lastIndex], k1[lastIndex], k2[lastIndex])
		val parent = (position - 1) ushr 1
		if (position > 0 && compareKeys(k1[position], k2[position], k1[parent], k2[parent]) < 0) siftUp(position)
		else siftDown(position)
	}

	private fun place(position: Int, node: Long, first: Double, second: Double) {
		nodes[position] = node
		k1[position] = first
		k2[position] = second
		index.put(node, position)
	}

	private fun siftUp(start: Int) {
		var position = start
		val node = nodes[position]
		val first = k1[position]
		val second = k2[position]
		while (position > 0) {
			val parent = (position - 1) ushr 1
			if (compareKeys(first, second, k1[parent], k2[parent]) >= 0) break
			place(position, nodes[parent], k1[parent], k2[parent])
			position = parent
		}
		place(position, node, first, second)
	}

	private fun siftDown(start: Int) {
		var position = start
		val size = count
		val node = nodes[position]
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
			place(position, nodes[child], k1[child], k2[child])
			position = child
		}
		place(position, node, first, second)
	}

	companion object {

		fun compareKeys(a1: Double, a2: Double, b1: Double, b2: Double): Int {
			val first = a1.compareTo(b1)
			return if (first != 0) first else a2.compareTo(b2)
		}
	}
}
