package com.lambda.pathing.coarse

import it.unimi.dsi.fastutil.doubles.DoubleArrayList
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntArrays
import it.unimi.dsi.fastutil.longs.LongArrayList
import java.util.Arrays

internal class RowEdges(
	val templates: IntArray,
	val edges: LongArray,
	val standing: LongArray,
	val irregular: LongArray,
	val pending: LongArray,
	private val irregularKeys: IntArray,
	private val irregularTicks: DoubleArray,
) {
	private var resolvedTicks: Int2DoubleOpenHashMap? = null

	val size: Int get() = templates.size

	val bytes: Int = ROW_BYTES + templates.size * ENTRY_BYTES + irregularKeys.size * IRREGULAR_BYTES

	fun ticksOf(template: Int, bit: Int): Double {
		val key = key(template, bit)
		val index = Arrays.binarySearch(irregularKeys, key)
		return if (index >= 0) irregularTicks[index] else resolvedTicks!!.get(key)
	}

	fun resolve(index: Int, bit: Int, found: Boolean, standingStart: Boolean, irregularTicks: Double) {
		val mask = 1L shl bit
		pending[index] = pending[index] and mask.inv()
		if (!found) return
		edges[index] = edges[index] or mask
		if (standingStart) standing[index] = standing[index] or mask
		if (irregularTicks.isNaN()) return
		irregular[index] = irregular[index] or mask
		(resolvedTicks ?: Int2DoubleOpenHashMap().also { resolvedTicks = it }).put(key(templates[index], bit), irregularTicks)
	}

	fun indexOf(template: Int, rank: IntArray): Int {
		var low = 0
		var high = templates.size - 1
		val wanted = rank[template]
		while (low <= high) {
			val middle = (low + high) ushr 1
			val found = rank[templates[middle]]
			when {
				found < wanted -> low = middle + 1
				found > wanted -> high = middle - 1
				else -> return middle
			}
		}
		return -1
	}

	fun groupStart(group: Int, groupOf: IntArray): Int {
		var low = 0
		var high = templates.size
		while (low < high) {
			val middle = (low + high) ushr 1
			if (groupOf[templates[middle]] < group) low = middle + 1 else high = middle
		}
		return low
	}

	class Builder {
		private val templates = IntArrayList()
		private val masks = LongArrayList()
		private val irregularKeys = IntArrayList()
		private val irregularTicks = DoubleArrayList()

		fun irregular(template: Int, bit: Int, ticks: Double) {
			irregularKeys.add(key(template, bit))
			irregularTicks.add(ticks)
		}

		fun add(template: Int, edges: Long, standing: Long, irregular: Long, pending: Long = 0L) {
			templates.add(template)
			masks.add(edges)
			masks.add(standing)
			masks.add(irregular)
			masks.add(pending)
		}

		fun build(rank: IntArray? = null): RowEdges {
			if (templates.isEmpty) return EMPTY
			val entries = IntArray(templates.size) { it }
			if (rank != null) IntArrays.quickSort(entries) { a, b -> rank[templates.getInt(a)].compareTo(rank[templates.getInt(b)]) }
			val order = IntArray(irregularKeys.size) { it }
			IntArrays.quickSort(order) { a, b -> irregularKeys.getInt(a).compareTo(irregularKeys.getInt(b)) }
			val edges = LongArray(entries.size) { masks.getLong(entries[it] * 4) }
			val standing = LongArray(entries.size) { masks.getLong(entries[it] * 4 + 1) }
			val irregular = LongArray(entries.size) { masks.getLong(entries[it] * 4 + 2) }
			val pending = LongArray(entries.size) { masks.getLong(entries[it] * 4 + 3) }
			val settled = pending.all { it == 0L }
			return RowEdges(
				IntArray(entries.size) { templates.getInt(entries[it]) },
				edges,
				if (settled && standing.contentEquals(edges)) edges else standing,
				if (settled && irregular.all { it == 0L }) zeros(entries.size) else irregular,
				if (settled) zeros(entries.size) else pending,
				IntArray(order.size) { irregularKeys.getInt(order[it]) },
				DoubleArray(order.size) { irregularTicks.getDouble(order[it]) },
			)
		}
	}

	companion object {
		val EMPTY = RowEdges(IntArray(0), LongArray(0), LongArray(0), LongArray(0), LongArray(0), IntArray(0), DoubleArray(0))

		@Volatile
		private var ZEROS = LongArray(64)

		private fun zeros(size: Int): LongArray {
			if (ZEROS.size < size) ZEROS = LongArray(Integer.highestOneBit(size) shl 1)
			return ZEROS
		}

		private fun key(template: Int, bit: Int): Int = (template shl 6) or bit

		private const val ROW_BYTES = 160

		private const val ENTRY_BYTES = 4 + 4 * 8

		private const val IRREGULAR_BYTES = 4 + 8
	}
}
