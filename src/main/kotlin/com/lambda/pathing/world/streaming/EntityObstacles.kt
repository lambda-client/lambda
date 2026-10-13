package com.lambda.pathing.world.streaming

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap
import it.unimi.dsi.fastutil.longs.LongArrayList
import it.unimi.dsi.fastutil.longs.LongOpenHashSet

internal class EntityObstacles(private val lingerTicks: Long = LINGER_TICKS) {
	private val expiries = Long2LongOpenHashMap()
	private var tick = 0L

	@Volatile
	var cells: LongArray = LongArray(0)
		private set

	operator fun contains(cell: Long): Boolean = expiries.containsKey(cell)

	fun update(occupied: LongOpenHashSet, changed: LongArrayList, released: (Long) -> Boolean = { false }) {
		tick++
		val occupiedCells = occupied.iterator()
		while (occupiedCells.hasNext()) {
			val cell = occupiedCells.nextLong()
			if (!expiries.containsKey(cell)) changed.add(cell)
			expiries.put(cell, tick + lingerTicks)
		}
		val entries = expiries.long2LongEntrySet().fastIterator()
		while (entries.hasNext()) {
			val entry = entries.next()
			val cell = entry.longKey
			if (entry.longValue > tick && !released(cell)) continue
			entries.remove()
			changed.add(cell)
		}
		if (!changed.isEmpty) cells = expiries.keys.toLongArray()
	}

	companion object {
		const val LINGER_TICKS = 60L
	}
}
