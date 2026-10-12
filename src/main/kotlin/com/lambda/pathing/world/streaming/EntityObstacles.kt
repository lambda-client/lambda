package com.lambda.pathing.world.streaming

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap
import it.unimi.dsi.fastutil.longs.LongArrayList
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkSectionPos

internal class EntityObstacles(private val lingerTicks: Long = LINGER_TICKS) {
	private val expiries = Long2LongOpenHashMap()
	private val perSection = Long2IntOpenHashMap()
	private var tick = 0L

	@Volatile
	var cells: LongArray = LongArray(0)
		private set

	operator fun contains(cell: Long): Boolean = expiries.containsKey(cell)

	fun inSection(sectionKey: Long): Boolean = perSection.get(sectionKey) > 0

	fun update(occupied: LongOpenHashSet, changed: LongArrayList, released: (Long) -> Boolean = { false }) {
		tick++
		val occupiedCells = occupied.iterator()
		while (occupiedCells.hasNext()) {
			val cell = occupiedCells.nextLong()
			if (!expiries.containsKey(cell)) {
				perSection.addTo(sectionOf(cell), 1)
				changed.add(cell)
			}
			expiries.put(cell, tick + lingerTicks)
		}
		val entries = expiries.long2LongEntrySet().fastIterator()
		while (entries.hasNext()) {
			val entry = entries.next()
			val cell = entry.longKey
			if (entry.longValue > tick && !released(cell)) continue
			entries.remove()
			val section = sectionOf(cell)
			if (perSection.addTo(section, -1) <= 1) perSection.remove(section)
			changed.add(cell)
		}
		if (!changed.isEmpty) cells = expiries.keys.toLongArray()
	}

	private fun sectionOf(cell: Long): Long =
		ChunkSectionPos.asLong(BlockPos.unpackLongX(cell) shr 4, BlockPos.unpackLongY(cell) shr 4, BlockPos.unpackLongZ(cell) shr 4)

	companion object {
		const val LINGER_TICKS = 60L
	}
}
