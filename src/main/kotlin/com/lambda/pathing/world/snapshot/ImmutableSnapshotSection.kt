package com.lambda.pathing.world.snapshot

internal class ImmutableSnapshotSection private constructor(
	private val palette: Array<SnapshotBlockPhysics>,
	private val byteIndices: ByteArray?,
	private val shortIndices: ShortArray?,
) {
	val paletteSize: Int get() = palette.size
	val indexStorageBytes: Int get() = byteIndices?.size ?: shortIndices?.size?.times(Short.SIZE_BYTES) ?: 0

	operator fun get(x: Int, y: Int, z: Int): SnapshotBlockPhysics {
		if (palette.size == 1) return palette[0]
		val localIndex = localIndex(x, y, z)
		val paletteIndex = byteIndices?.get(localIndex)?.toInt()?.and(0xff)
			?: shortIndices?.get(localIndex)?.toInt()?.and(0xffff)
			?: error("Non-uniform snapshot section has no indices")
		return palette[paletteIndex]
	}

	class Builder {
		private val cells = Array(SECTION_CELLS) { SnapshotBlockPhysics.AIR }
		private var writes = 0

		fun set(x: Int, y: Int, z: Int, physics: SnapshotBlockPhysics) {
			cells[localIndex(x, y, z)] = physics
			writes++
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

	private companion object {
		const val SECTION_CELLS = 16 * 16 * 16

		fun localIndex(x: Int, y: Int, z: Int): Int =
			((y and 15) shl 8) or ((z and 15) shl 4) or (x and 15)
	}
}
