package com.lambda.pathing.world

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.util.math.ChunkPos
import net.minecraft.util.math.ChunkSectionPos

internal class ColumnOccupancy(private val view: CoarseVoxelView) {
	private val sections = Long2ObjectLinkedOpenHashMap<ShortArray>()
	private val unknown = LongOpenHashSet()
	private val tops = Long2ObjectOpenHashMap<IntArray>()
	private val scratch = Array(CellClass.PLANES) { LongArray(SECTION_WORDS) }
	private var revision: Any? = view.revision
	private var lastKey = 0L
	private var last: ShortArray? = null

	private val minY = view.simulableStanceY.first.let { if (it == Int.MIN_VALUE) DEFAULT_MIN_Y else it - FLOOR_MARGIN }
	private val maxY = view.simulableStanceY.last.let { if (it == Int.MAX_VALUE) DEFAULT_MAX_Y else it + CEILING_MARGIN }

	fun synchronize(): Boolean {
		val current = view.revision
		if (current === revision) return false
		revision = current
		clear()
		return true
	}

	fun clear() {
		sections.clear()
		unknown.clear()
		tops.clear()
		last = null
	}

	fun occupied(x: Int, y: Int, z: Int): Boolean {
		if (y < minY || y > maxY) return true
		return column(x, y, z).toInt() and (1 shl (y and 15)) != 0
	}

	fun highestAtOrBelow(x: Int, y: Int, z: Int, lowest: Int): Int {
		var top = minOf(y, maxY)
		val bottom = maxOf(lowest, minY)
		if (y > maxY) return y
		while (top >= bottom) {
			val base = top and 15.inv()
			val mask = column(x, top, z).toInt() and ((2 shl (top and 15)) - 1)
			if (mask != 0) {
				val found = base + 31 - Integer.numberOfLeadingZeros(mask)
				return if (found >= bottom) found else Int.MIN_VALUE
			}
			top = base - 1
		}
		return if (lowest < minY) minY - 1 else Int.MIN_VALUE
	}

	fun lowestAbove(x: Int, y: Int, z: Int, highest: Int): Int {
		var bottom = maxOf(y + 1, minY)
		val top = minOf(highest, maxY)
		if (y + 1 < minY) return y + 1
		while (bottom <= top) {
			val base = bottom and 15.inv()
			val mask = column(x, bottom, z).toInt() and (0xFFFF shl (bottom and 15)) and 0xFFFF
			if (mask != 0) {
				val found = base + Integer.numberOfTrailingZeros(mask)
				return when {
					found <= top -> found
					highest > maxY -> maxY + 1
					else -> Int.MAX_VALUE
				}
			}
			bottom = base + 16
		}
		return if (highest > maxY) maxY + 1 else Int.MAX_VALUE
	}

	fun highestEmptyAtOrBelow(x: Int, y: Int, z: Int, lowest: Int): Int {
		var top = minOf(y, maxY)
		val bottom = maxOf(lowest, minY)
		if (y > maxY) return y
		while (top >= bottom) {
			val base = top and 15.inv()
			val mask = column(x, top, z).toInt().inv() and ((2 shl (top and 15)) - 1)
			if (mask != 0) {
				val found = base + 31 - Integer.numberOfLeadingZeros(mask)
				return if (found >= bottom) found else Int.MIN_VALUE
			}
			top = base - 1
		}
		return Int.MIN_VALUE
	}

	fun top(x: Int, z: Int): Int {
		val key = ChunkPos.toLong(x shr 4, z shr 4)
		val columns = tops.get(key) ?: knownTops(x shr 4, z shr 4).also { tops.put(key, it) }
		return columns[((z and 15) shl 4) or (x and 15)]
	}

	private fun knownTops(chunkX: Int, chunkZ: Int): IntArray {
		val columns = IntArray(COLUMNS) { Int.MIN_VALUE }
		var unresolved = COLUMNS
		var sectionY = maxY shr 4
		while (sectionY >= minY shr 4 && unresolved > 0) {
			val key = ChunkSectionPos.asLong(chunkX, sectionY, chunkZ)
			val masks = section(key)
			if (key !in unknown) {
				for (column in 0 until COLUMNS) {
					val mask = masks[column].toInt() and 0xFFFF
					if (columns[column] != Int.MIN_VALUE || mask == 0) continue
					columns[column] = (sectionY shl 4) + 31 - Integer.numberOfLeadingZeros(mask)
					unresolved--
				}
			}
			sectionY--
		}
		return columns
	}

	private fun column(x: Int, y: Int, z: Int): Short {
		val key = ChunkSectionPos.asLong(x shr 4, y shr 4, z shr 4)
		val masks = if (key == lastKey && last != null) last!! else section(key).also {
			lastKey = key
			last = it
		}
		return masks[((z and 15) shl 4) or (x and 15)]
	}

	private fun section(key: Long): ShortArray {
		sections.getAndMoveToLast(key)?.let { return it }
		for (plane in scratch) plane.fill(0L)
		view.classifySection(ChunkSectionPos.unpackX(key), ChunkSectionPos.unpackY(key), ChunkSectionPos.unpackZ(key), scratch)
		val masks = ShortArray(COLUMNS)
		for (word in 0 until SECTION_WORDS) {
			var bits = 0L
			for (plane in scratch) bits = bits or plane[word]
			if (bits == 0L) continue
			val y = word ushr 2
			val zBase = (word and 3) shl 2
			while (bits != 0L) {
				val bit = java.lang.Long.numberOfTrailingZeros(bits)
				bits = bits and (bits - 1)
				val column = ((zBase + (bit ushr 4)) shl 4) or (bit and 15)
				masks[column] = (masks[column].toInt() or (1 shl y)).toShort()
			}
		}
		val sectionX = ChunkSectionPos.unpackX(key)
		val sectionY = ChunkSectionPos.unpackY(key)
		val sectionZ = ChunkSectionPos.unpackZ(key)
		if (!view.isKnown(sectionX shl 4, sectionY shl 4, sectionZ shl 4)) unknown.add(key) else unknown.remove(key)
		if (sections.size >= CAPACITY) sections.removeFirst()
		sections.putAndMoveToLast(key, masks)
		return masks
	}

	private companion object {
		const val COLUMNS = 256
		const val SECTION_WORDS = SECTION_CELL_COUNT / 64
		const val CAPACITY = 4096
		const val FLOOR_MARGIN = 2
		const val CEILING_MARGIN = 4
		const val DEFAULT_MIN_Y = -64
		const val DEFAULT_MAX_Y = 319
	}
}
