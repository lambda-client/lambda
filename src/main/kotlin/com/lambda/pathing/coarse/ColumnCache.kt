package com.lambda.pathing.coarse

import com.lambda.pathing.core.CellBox
import com.lambda.pathing.core.PathingChunk
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongOpenHashSet

internal class ColumnCache<T : Any>(private val capacity: Int) {
	private val values = Long2ObjectLinkedOpenHashMap<T>()
	private val spans = Long2LongOpenHashMap()
	private val columns = Long2ObjectOpenHashMap<LongOpenHashSet>()

	operator fun get(key: Long): T? = values.getAndMoveToLast(key)

	fun put(key: Long, value: T, lowest: Int, highest: Int) {
		if (values.size >= capacity) remove(values.firstLongKey())
		values.putAndMoveToLast(key, value)
		spans.put(key, (lowest.toLong() shl 32) or (highest.toLong() and 0xFFFFFFFFL))
		columns.computeIfAbsent(column(key)) { LongOpenHashSet() }.add(key)
	}

	fun forget(box: CellBox, reach: Int) {
		val minX = box.xs.first - reach
		val maxX = box.xs.last + reach
		val minZ = box.zs.first - reach
		val maxZ = box.zs.last + reach
		for (chunkX in (minX shr 4)..(maxX shr 4)) for (chunkZ in (minZ shr 4)..(maxZ shr 4)) {
			val column = PathingChunk.key(chunkX, chunkZ)
			val keys = columns.get(column) ?: continue
			val iterator = keys.iterator()
			while (iterator.hasNext()) {
				val key = iterator.nextLong()
				val x = PackedStance.unpackX(key)
				val z = PackedStance.unpackZ(key)
				if (x < minX || x > maxX || z < minZ || z > maxZ) continue
				val span = spans.get(key)
				if ((span shr 32).toInt() > box.ys.last || span.toInt() < box.ys.first) continue
				iterator.remove()
				values.remove(key)
				spans.remove(key)
			}
			if (keys.isEmpty()) columns.remove(column)
		}
	}

	fun clear() {
		values.clear()
		spans.clear()
		columns.clear()
	}

	private fun remove(key: Long) {
		values.remove(key)
		spans.remove(key)
		val column = column(key)
		val keys = columns.get(column) ?: return
		keys.remove(key)
		if (keys.isEmpty()) columns.remove(column)
	}

	private fun column(key: Long): Long = PathingChunk.key(PackedStance.unpackX(key) shr 4, PackedStance.unpackZ(key) shr 4)
}
