package com.lambda.pathing.world.snapshot

import net.minecraft.util.math.BlockPos

data class SimulationSnapshotBounds(
	val minX: Int,
	val minY: Int,
	val minZ: Int,
	val maxX: Int,
	val maxY: Int,
	val maxZ: Int,
) {
	init {
		require(minX <= maxX && minY <= maxY && minZ <= maxZ) { "Invalid snapshot bounds: $this" }
	}

	operator fun contains(pos: BlockPos): Boolean =
		pos.x in minX..maxX && pos.y in minY..maxY && pos.z in minZ..maxZ

	val simulableStanceY: IntRange get() = (minY + FLOOR_REACH)..(maxY - CEILING_REACH)

	companion object {
		const val CEILING_REACH = 4
		const val FLOOR_REACH = 2
	}
}
