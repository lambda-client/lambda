package com.lambda.pathing.world.streaming

import com.lambda.pathing.core.PathingChunk
import com.lambda.pathing.core.VoxelPos

sealed interface WorldMutation {
	val revision: Long

	data class Cell(
		val cell: VoxelPos,
		override val revision: Long,
	) : WorldMutation

	data class Chunk(
		val chunk: PathingChunk,
		override val revision: Long,
	) : WorldMutation
}
