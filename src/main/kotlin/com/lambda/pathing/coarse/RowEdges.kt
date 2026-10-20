package com.lambda.pathing.coarse

import it.unimi.dsi.fastutil.doubles.DoubleArrayList
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntArrays
import it.unimi.dsi.fastutil.longs.LongArrayList
import java.util.*

internal class RowEdges private constructor(
	private val templates: ShortArray,
	private val byTemplate: ShortArray?,
	private val groupStarts: ShortArray?,
	private val support: Long,
	private val width: Int,
	private val masks: LongArray,
	private val irregularKeys: IntArray,
	private val irregularTicks: DoubleArray,
) {
	private var resolvedTicks: Int2DoubleOpenHashMap? = null

	var referenced = false

	val size: Int get() = templates.size

	val bytes: Int = ROW_BYTES + templates.size * 2 + (byTemplate?.size ?: 0) * 2 + (groupStarts?.size ?: 0) * 2 +
			masks.size * 8 + irregularKeys.size * IRREGULAR_BYTES

	fun template(index: Int): Int = templates[index].toInt() and 0xFFFF

	fun byTemplate(position: Int): Int = byTemplate?.let { it[position].toInt() and 0xFFFF } ?: position

	fun groupStart(group: Int): Int = groupStarts!![group].toInt() and 0xFFFF

	fun lane(bit: Int): Int {
		val mask = 1L shl bit
		return if (support and mask == 0L) -1 else java.lang.Long.bitCount(support and (mask - 1))
	}

	fun edges(index: Int): Long = expand(index, EDGES)

	fun standing(index: Int): Long = expand(index, STANDING)

	fun irregular(index: Int): Long = expand(index, IRREGULAR)

	fun pending(index: Int): Long = expand(index, PENDING)

	fun hasEdge(index: Int, lane: Int): Boolean = test(index, EDGES, lane)

	fun isStanding(index: Int, lane: Int): Boolean = test(index, STANDING, lane)

	fun isIrregular(index: Int, lane: Int): Boolean = test(index, IRREGULAR, lane)

	fun isPending(index: Int, lane: Int): Boolean = test(index, PENDING, lane)

	fun ticksOf(template: Int, bit: Int): Double {
		val key = key(template, bit)
		val index = Arrays.binarySearch(irregularKeys, key)
		return if (index >= 0) irregularTicks[index] else resolvedTicks!!.get(key)
	}

	fun irregularStart(template: Int): Int {
		val wanted = key(template, 0)
		var low = 0
		var high = irregularKeys.size
		while (low < high) {
			val middle = (low + high) ushr 1
			if (irregularKeys[middle] < wanted) low = middle + 1 else high = middle
		}
		return low
	}

	fun ticksOf(template: Int, bit: Int, start: Int): Double {
		val key = key(template, bit)
		val index = Arrays.binarySearch(irregularKeys, start, minOf(start + Long.SIZE_BITS, irregularKeys.size), key)
		return if (index >= 0) irregularTicks[index] else resolvedTicks!!.get(key)
	}

	fun resolve(index: Int, bit: Int, found: Boolean, standingStart: Boolean, irregularTicks: Double) {
		val lane = lane(bit)
		if (lane < 0) return
		clearPending(index, lane)
		if (!found) return
		set(index, EDGES, lane)
		if (standingStart) set(index, STANDING, lane)
		if (irregularTicks.isNaN()) return
		set(index, IRREGULAR, lane)
		(resolvedTicks ?: Int2DoubleOpenHashMap().also { resolvedTicks = it }).put(key(template(index), bit), irregularTicks)
	}

	fun indexOf(template: Int, rank: IntArray): Int {
		var low = 0
		var high = templates.size - 1
		val wanted = rank[template]
		while (low <= high) {
			val middle = (low + high) ushr 1
			val found = rank[template(middle)]
			when {
				found < wanted -> low = middle + 1
				found > wanted -> high = middle - 1
				else -> return middle
			}
		}
		return -1
	}

	private fun offset(index: Int, field: Int): Int = ((index shl 2) + field) shl width

	private fun expand(index: Int, field: Int): Long {
		val at = offset(index, field)
		val word = masks[at ushr 6]
		val packed = if (width == 6) word else (word ushr (at and 63)) and ((1L shl (1 shl width)) - 1)
		return if (packed == 0L || support == -1L) packed else java.lang.Long.expand(packed, support)
	}

	private fun test(index: Int, field: Int, lane: Int): Boolean {
		if (lane < 0) return false
		val at = offset(index, field) + lane
		return masks[at ushr 6] and (1L shl (at and 63)) != 0L
	}

	private fun set(index: Int, field: Int, lane: Int) {
		val at = offset(index, field) + lane
		masks[at ushr 6] = masks[at ushr 6] or (1L shl (at and 63))
	}

	private fun clearPending(index: Int, lane: Int) {
		val at = offset(index, PENDING) + lane
		masks[at ushr 6] = masks[at ushr 6] and (1L shl (at and 63)).inv()
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

		fun build(groups: TemplateGroups? = null): RowEdges {
			if (templates.isEmpty) return EMPTY
			val entries = IntArray(templates.size) { it }
			val rank = groups?.rank
			if (rank != null) IntArrays.quickSort(entries) { a, b -> rank[templates.getInt(a)].compareTo(rank[templates.getInt(b)]) }
			val order = IntArray(irregularKeys.size) { it }
			IntArrays.quickSort(order) { a, b -> irregularKeys.getInt(a).compareTo(irregularKeys.getInt(b)) }
			var support = 0L
			for (index in 0 until masks.size) support = support or masks.getLong(index)
			val width = WIDTHS[java.lang.Long.bitCount(support)]
			val packed = LongArray(((entries.size shl 2 shl width) + 63) ushr 6)
			for (position in entries.indices) for (field in 0 until FIELDS) {
				val mask = masks.getLong(entries[position] * FIELDS + field)
				if (mask == 0L) continue
				val compressed = if (support == -1L) mask else java.lang.Long.compress(mask, support)
				val at = ((position shl 2) + field) shl width
				packed[at ushr 6] = packed[at ushr 6] or (compressed shl (at and 63))
			}
			val ordered = ShortArray(entries.size) { templates.getInt(entries[it]).toShort() }
			val byTemplate = if (rank == null) null else {
				val positions = IntArray(entries.size) { it }
				IntArrays.quickSort(positions) { a, b -> (ordered[a].toInt() and 0xFFFF).compareTo(ordered[b].toInt() and 0xFFFF) }
				ShortArray(entries.size) { positions[it].toShort() }
			}
			val groupStarts = if (groups == null) null else ShortArray(groups.size + 1).also { starts ->
				var index = 0
				for (group in starts.indices) {
					while (index < ordered.size && groups.of[ordered[index].toInt() and 0xFFFF] in 0 until group) index++
					starts[group] = index.toShort()
				}
			}
			return RowEdges(
				ordered,
				byTemplate,
				groupStarts,
				support,
				width,
				packed,
				IntArray(order.size) { irregularKeys.getInt(order[it]) },
				DoubleArray(order.size) { irregularTicks.getDouble(order[it]) },
			)
		}
	}

	companion object {
		val EMPTY = RowEdges(ShortArray(0), null, null, 0L, 0, LongArray(0), IntArray(0), DoubleArray(0))

		private const val EDGES = 0
		private const val STANDING = 1
		private const val IRREGULAR = 2
		private const val PENDING = 3
		private const val FIELDS = 4

		private val WIDTHS = IntArray(Long.SIZE_BITS + 1) { lanes -> if (lanes <= 1) 0 else 32 - Integer.numberOfLeadingZeros(lanes - 1) }

		private fun key(template: Int, bit: Int): Int = (template shl 6) or bit

		private const val ROW_BYTES = 160

		private const val IRREGULAR_BYTES = 4 + 8
	}
}
