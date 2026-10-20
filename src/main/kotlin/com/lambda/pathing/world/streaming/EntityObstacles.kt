package com.lambda.pathing.world.streaming

import it.unimi.dsi.fastutil.longs.LongOpenHashSet

internal class EntityObstacles {
	private val current = LongOpenHashSet()

	@Volatile
	var cells: LongArray = LongArray(0)
		private set

	operator fun contains(cell: Long): Boolean = current.contains(cell)

	fun update(occupied: LongOpenHashSet) {
		if (occupied == current) return
		current.clear()
		current.addAll(occupied)
		cells = current.toLongArray()
	}
}
