package com.lambda.pathing.search

import com.lambda.pathing.core.VoxelPos

internal class FrameDependencyIndex<K : Any>(frames: List<Set<VoxelPos>>, keyOf: (VoxelPos) -> K) {
	private class ReadWindow(val first: Int, var last: Int)

	private val reads = buildMap<K, ReadWindow> {
		frames.forEachIndexed { frame, positions ->
			positions.forEach { position ->
				getOrPut(keyOf(position)) { ReadWindow(frame, frame) }.last = frame
			}
		}
	}

	fun first(key: K): Int? = reads[key]?.first

	fun from(frame: Int): Set<K> = select { it.last >= frame }

	fun between(frame: Int, untilFrame: Int): Set<K> = select { it.last >= frame && it.first < untilFrame }

	private inline fun select(predicate: (ReadWindow) -> Boolean): Set<K> =
		reads.mapNotNullTo(linkedSetOf()) { (key, window) -> key.takeIf { predicate(window) } }
}
