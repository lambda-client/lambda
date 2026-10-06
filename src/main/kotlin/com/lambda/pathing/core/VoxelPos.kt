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
