package com.lambda.pathing.world

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import net.minecraft.util.math.ChunkSectionPos

internal class TerrainBits(private val view: CoarseVoxelView) {
	private val blocks = Long2ObjectOpenHashMap<LongArray>()
	private val slotKeys = LongArray(SLOTS)
	private val slotBlocks = arrayOfNulls<LongArray>(SLOTS)
	private val scratch = Array(CellClass.PLANES) { LongArray(SECTION_WORDS) }
	private var revision: Any? = view.revision

	fun synchronize(): Boolean {
		val current = view.revision
		if (current === revision) return false
		revision = current
		clear()
		return true
	}

	fun clear() {
		blocks.clear()
		slotBlocks.fill(null)
	}

	fun fetch(word: Int, y: Int, z: Int, planes: Array<LongArray>, at: Int) {
		val row = ((y and 15) shl 4) + (z and 15)
		for (offset in 0..2) {
			val block = block(word - 1 + offset, y shr 4, z shr 4)
			for (plane in planes.indices) planes[plane][at + offset] = block[plane * ROWS + row]
		}
	}

	private fun block(word: Int, sectionY: Int, sectionZ: Int): LongArray {
		val key = ChunkSectionPos.asLong(word, sectionY, sectionZ)
		val slot = ((word and 3) shl 4) or ((sectionY and 3) shl 2) or (sectionZ and 3)
		val cached = slotBlocks[slot]
		if (cached != null && slotKeys[slot] == key) return cached
		val block = blocks.get(key) ?: build(word, sectionY, sectionZ).also { blocks.put(key, it) }
		slotKeys[slot] = key
		slotBlocks[slot] = block
		return block
	}

	private fun build(word: Int, sectionY: Int, sectionZ: Int): LongArray {
		val block = LongArray(ROWS * CellClass.PLANES)
		for (lane in 0 until LANES) {
			for (plane in scratch) plane.fill(0L)
			view.classifySection(word * LANES + lane, sectionY, sectionZ, scratch)
			val laneShift = lane shl 4
			for (row in 0 until ROWS) {
				val source = ((row ushr 4) shl 2) or ((row and 15) ushr 2)
				val shift = (row and 3) shl 4
				for (plane in scratch.indices) {
					val bits = (scratch[plane][source] ushr shift) and LANE_MASK
					block[plane * ROWS + row] = block[plane * ROWS + row] or (bits shl laneShift)
				}
			}
		}
		return block
	}

	private companion object {
		const val LANES = 4
		const val ROWS = 256
		const val SLOTS = 64
		const val SECTION_WORDS = SECTION_CELL_COUNT / 64
		const val LANE_MASK = 0xFFFFL
	}
}
