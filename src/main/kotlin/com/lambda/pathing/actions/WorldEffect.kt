package com.lambda.pathing.actions

import com.lambda.pathing.core.VoxelPos

sealed interface WorldEffect {

	data class Place(val at: VoxelPos, val block: BlockKind) : WorldEffect

	data class Break(val at: VoxelPos) : WorldEffect

	data class Toggle(val at: VoxelPos) : WorldEffect
}

enum class BlockKind {
	@Suppress("unused")
	SOLID,
}
