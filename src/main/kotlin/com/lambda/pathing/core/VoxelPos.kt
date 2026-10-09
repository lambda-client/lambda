package com.lambda.pathing.core

data class VoxelPos(val x: Int, val y: Int, val z: Int)

data class PathingSection(val x: Int, val y: Int, val z: Int) {
	companion object {
		fun containing(pos: VoxelPos) = PathingSection(pos.x shr 4, pos.y shr 4, pos.z shr 4)
	}
}

data class PathingChunk(val x: Int, val z: Int) {
	companion object {
		fun containing(pos: VoxelPos) = PathingChunk(pos.x shr 4, pos.z shr 4)

		fun key(x: Int, z: Int): Long = (x.toLong() and 0xFFFFFFFFL) or ((z.toLong() and 0xFFFFFFFFL) shl 32)
	}
}

data class CellBox(val xs: IntRange, val ys: IntRange, val zs: IntRange) {
	fun contains(x: Int, y: Int, z: Int): Boolean = x in xs && y in ys && z in zs

	fun overlaps(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): Boolean =
		xs.first <= maxX && minX <= xs.last && ys.first <= maxY && minY <= ys.last && zs.first <= maxZ && minZ <= zs.last

	fun grown(lowX: Int, highX: Int, lowY: Int, highY: Int, lowZ: Int, highZ: Int): CellBox =
		CellBox((xs.first + lowX)..(xs.last + highX), (ys.first + lowY)..(ys.last + highY), (zs.first + lowZ)..(zs.last + highZ))

	companion object {
		private val ANY_Y = (Int.MIN_VALUE / 2)..(Int.MAX_VALUE / 2)

		fun column(chunk: PathingChunk): CellBox = CellBox((chunk.x shl 4)..((chunk.x shl 4) + 15), ANY_Y, (chunk.z shl 4)..((chunk.z shl 4) + 15))

		fun bounding(cells: Collection<VoxelPos>): CellBox = CellBox(
			cells.minOf(VoxelPos::x)..cells.maxOf(VoxelPos::x),
			cells.minOf(VoxelPos::y)..cells.maxOf(VoxelPos::y),
			cells.minOf(VoxelPos::z)..cells.maxOf(VoxelPos::z),
		)

		fun sections(chunk: PathingChunk, lowestY: Int, highestY: Int): CellBox =
			CellBox((chunk.x shl 4)..((chunk.x shl 4) + 15), (lowestY shl 4)..((highestY shl 4) + 15), (chunk.z shl 4)..((chunk.z shl 4) + 15))
	}
}
