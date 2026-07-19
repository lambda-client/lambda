
package com.minato.util

import com.minato.context.SafeContext
import com.minato.util.BlockUtils.blockState
import com.minato.util.BlockUtils.isNotEmpty
import com.minato.util.math.MathUtils.ceilToInt
import fi.dy.masa.litematica.data.DataManager
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

object PlayerBuildLayerUtils {
	fun SafeContext.isInFlatten(pos: BlockPos, flattenMode: FlattenMode, sneakLowersFlatten: Boolean): Boolean {
		if (flattenMode == FlattenMode.None) return true

		if (flattenMode == FlattenMode.Staircase) {
			val up = pos.up()
			if ((blockState(up).isNotEmpty) ||
				(blockState(up.east()).isNotEmpty) ||
				(blockState(up.south()).isNotEmpty) ||
				(blockState(up.west()).isNotEmpty) ||
				(blockState(up.north()).isNotEmpty)
			) { return false }
		}

		val flattenY = player.y.ceilToInt()
		val playerPos = player.blockPos
		val flattenLevel =
			if (sneakLowersFlatten && player.isSneaking) flattenY - 1
			else flattenY

		if (!flattenMode.isSmart && pos.y < flattenLevel) return false

		if (pos == player.supportingBlockPos) return false

		val playerLookDir = player.horizontalFacing
		val smartFlattenDir =
			if (flattenMode == FlattenMode.Smart) playerLookDir
			else playerLookDir?.opposite

		if (pos.y >= flattenLevel) return true

		val zeroedPos = pos.add(-playerPos.x, -flattenY, -playerPos.z)

		return (zeroedPos.x < 0 && smartFlattenDir == Direction.EAST) ||
				(zeroedPos.z < 0 && smartFlattenDir == Direction.SOUTH) ||
				(zeroedPos.x > 0 && smartFlattenDir == Direction.WEST) ||
				(zeroedPos.z > 0 && smartFlattenDir == Direction.NORTH)
	}

	fun inSchematic(pos: BlockPos): Boolean {
		val placementManager = DataManager.getSchematicPlacementManager()
		return placementManager?.getAllPlacementsTouchingChunk(pos)?.any {
			it.placement.isEnabled && it.bb.containsPos(pos)
		} ?: false
	}

	enum class FlattenMode {
		None,
		Standard,
		Smart,
		ReverseSmart,
		Staircase;

		val isSmart
			get() = this == Smart || this == ReverseSmart
	}
}