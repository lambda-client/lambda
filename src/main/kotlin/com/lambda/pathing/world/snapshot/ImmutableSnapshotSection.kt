package com.lambda.pathing.world.snapshot

import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkSectionPos

internal class ImmutableSnapshotSection private constructor(
	private val palette: Array<SnapshotBlockPhysics>,
	private val byteIndices: ByteArray?,
	private val shortIndices: ShortArray?,
) {
	val paletteSize: Int get() = palette.size
	val bouncy: Boolean = palette.any { it.coarseVoxel.bouncy }
	val watery: Boolean = palette.any { it.water != null }
	val hot: Boolean = palette.any { it.hotFloor }
	val soft: Boolean = palette.any { it.water != null || it.fallRule.cushions && !it.coarseVoxel.bouncy }
	val indexStorageBytes: Int get() = byteIndices?.size ?: shortIndices?.size?.times(Short.SIZE_BYTES) ?: 0

	operator fun get(x: Int, y: Int, z: Int): SnapshotBlockPhysics = cell(localIndex(x, y, z))

	fun with(x: Int, y: Int, z: Int, physics: SnapshotBlockPhysics): ImmutableSnapshotSection =
		Builder(this).apply { replace(x, y, z, physics) }.build()

	private fun cell(index: Int): SnapshotBlockPhysics {
		if (palette.size == 1) return palette[0]
		byteIndices?.let { return palette[it[index].toInt() and 0xff] }
		val shorts = shortIndices ?: error("Non-uniform snapshot section has no indices")
		return palette[shorts[index].toInt() and 0xffff]
	}

	fun classify(planes: Array<LongArray>) {
		if (palette.size == 1) return palette[0].cellClass.fill(planes)
		val classes = Array(palette.size) { palette[it].cellClass }
		val bytes = byteIndices
		if (bytes != null) {
			for (index in 0 until SECTION_CELLS) classes[bytes[index].toInt() and 0xff].mark(planes, index)
			return
		}
		val shorts = shortIndices ?: error("Non-uniform snapshot section has no indices")
		for (index in 0 until SECTION_CELLS) classes[shorts[index].toInt() and 0xffff].mark(planes, index)
	}

	class Builder() {
		private val cells = Array(SECTION_CELLS) { SnapshotBlockPhysics.AIR }
		private var writes = 0

		internal constructor(source: ImmutableSnapshotSection) : this() {
			for (index in 0 until SECTION_CELLS) cells[index] = source.cell(index)
		}

		fun set(x: Int, y: Int, z: Int, physics: SnapshotBlockPhysics) {
			cells[localIndex(x, y, z)] = physics
			writes++
		}

		fun replace(x: Int, y: Int, z: Int, physics: SnapshotBlockPhysics) {
			cells[localIndex(x, y, z)] = physics
		}

		fun fill(physics: SnapshotBlockPhysics) {
			cells.fill(physics)
			writes = SECTION_CELLS
		}

		fun build(expectedWrites: Int? = null): ImmutableSnapshotSection {
			expectedWrites?.let {
				require(writes == it) { "Snapshot section captured $writes cells, expected $it" }
			}

			val palette = ArrayList<SnapshotBlockPhysics>()
			val paletteIndices = HashMap<SnapshotBlockPhysics, Int>()
			val indices = IntArray(SECTION_CELLS)
			cells.forEachIndexed { cellIndex, physics ->
				indices[cellIndex] = paletteIndices.getOrPut(physics) {
					palette.add(physics)
					palette.lastIndex
				}
			}

			if (palette.size == 1) {
				return ImmutableSnapshotSection(arrayOf(palette[0]), null, null)
			}
			if (palette.size <= 256) {
				return ImmutableSnapshotSection(
					palette.toTypedArray(),
					ByteArray(SECTION_CELLS) { indices[it].toByte() },
					null,
				)
			}
			return ImmutableSnapshotSection(
				palette.toTypedArray(),
				null,
				ShortArray(SECTION_CELLS) { indices[it].toShort() },
			)
		}
	}

	companion object {
		fun sectionsOf(blocks: Map<BlockPos, SnapshotBlockPhysics>): Map<Long, ImmutableSnapshotSection> {
			val builders = HashMap<Long, Builder>()
			blocks.forEach { (pos, physics) -> builders.getOrPut(ChunkSectionPos.toLong(pos), ::Builder).set(pos.x, pos.y, pos.z, physics) }
			return builders.mapValues { (_, builder) -> builder.build() }
		}

		private fun localIndex(x: Int, y: Int, z: Int): Int =
			((y and 15) shl 8) or ((z and 15) shl 4) or (x and 15)
	}
}

internal const val SECTION_CELLS = 16 * 16 * 16
