package com.lambda.pathing.coarse

import it.unimi.dsi.fastutil.doubles.DoubleArrayList
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntArrays
import it.unimi.dsi.fastutil.longs.LongArrayList
import java.util.Arrays

internal class RowEdges(
	val templates: IntArray,
	val edges: LongArray,
	val standing: LongArray,
	val irregular: LongArray,
	private val irregularKeys: IntArray,
	private val irregularTicks: DoubleArray,
) {
	val size: Int get() = templates.size

	fun ticksOf(template: Int, bit: Int): Double = irregularTicks[Arrays.binarySearch(irregularKeys, key(template, bit))]

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

		fun add(template: Int, edges: Long, standing: Long, irregular: Long) {
			templates.add(template)
			masks.add(edges)
			masks.add(standing)
			masks.add(irregular)
		}

		fun build(rank: IntArray? = null): RowEdges {
			if (templates.isEmpty) return EMPTY
			val entries = IntArray(templates.size) { it }
			if (rank != null) IntArrays.quickSort(entries) { a, b -> rank[templates.getInt(a)].compareTo(rank[templates.getInt(b)]) }
			val order = IntArray(irregularKeys.size) { it }
			IntArrays.quickSort(order) { a, b -> irregularKeys.getInt(a).compareTo(irregularKeys.getInt(b)) }
			return RowEdges(
				IntArray(entries.size) { templates.getInt(entries[it]) },
				LongArray(entries.size) { masks.getLong(entries[it] * 3) },
				LongArray(entries.size) { masks.getLong(entries[it] * 3 + 1) },
				LongArray(entries.size) { masks.getLong(entries[it] * 3 + 2) },
				IntArray(order.size) { irregularKeys.getInt(order[it]) },
				DoubleArray(order.size) { irregularTicks.getDouble(order[it]) },
			)
		}
	}

	companion object {
		val EMPTY = RowEdges(IntArray(0), LongArray(0), LongArray(0), LongArray(0), IntArray(0), DoubleArray(0))

		private fun key(template: Int, bit: Int): Int = (template shl 6) or bit
	}
}
