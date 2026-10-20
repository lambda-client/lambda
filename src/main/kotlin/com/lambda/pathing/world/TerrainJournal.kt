package com.lambda.pathing.world

import com.lambda.pathing.core.CellBox

class TerrainJournal(private val capacity: Int = DEFAULT_CAPACITY) {
	private val boxes = IntArray(capacity * BOX_INTS)

	@Volatile
	var version = 0L
		private set

	fun record(box: CellBox) = record(box.xs.first, box.ys.first, box.zs.first, box.xs.last, box.ys.last, box.zs.last)

	@Synchronized
	fun record(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int) {
		val slot = (version % capacity).toInt() * BOX_INTS
		boxes[slot] = minX
		boxes[slot + 1] = minY
		boxes[slot + 2] = minZ
		boxes[slot + 3] = maxX
		boxes[slot + 4] = maxY
		boxes[slot + 5] = maxZ
		version++
	}

	@Synchronized
	fun since(from: Long, to: Long): List<CellBox>? {
		if (version - from > capacity) return null
		return (from until to).map { entry ->
			val slot = (entry % capacity).toInt() * BOX_INTS
			CellBox(boxes[slot]..boxes[slot + 3], boxes[slot + 1]..boxes[slot + 4], boxes[slot + 2]..boxes[slot + 5])
		}
	}

	private companion object {
		const val DEFAULT_CAPACITY = 1 shl 16
		const val BOX_INTS = 6
	}
}

class TerrainWatch(private val view: CoarseVoxelView) {
	private var revision: Any? = view.revision
	private var version: Long = view.journal?.version ?: 0L

	inline fun poll(reset: () -> Unit, changed: (CellBox) -> Unit) {
		val boxes = changes() ?: return reset()
		for (box in boxes) changed(box)
	}

	fun changes(): List<CellBox>? {
		val current = view.revision
		val journal = view.journal
		if (current !== revision) {
			revision = current
			version = journal?.version ?: 0L
			return null
		}
		if (journal == null) return NONE
		val to = journal.version
		if (to == version) return NONE
		val boxes = journal.since(version, to)
		version = to
		return boxes
	}

	private companion object {
		val NONE = emptyList<CellBox>()
	}
}
