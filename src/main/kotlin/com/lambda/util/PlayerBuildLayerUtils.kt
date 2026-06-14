/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.util

import com.lambda.context.SafeContext
import com.lambda.interaction.BaritoneHandler
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.BlockUtils.isNotEmpty
import com.lambda.util.math.MathUtils.ceilToInt
import fi.dy.masa.litematica.data.DataManager
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

object PlayerBuildLayerUtils {
	fun SafeContext.isInFlatten(pos: BlockPos, flattenMode: FlattenMode, sneakLowersFlatten: Boolean, baritoneSelection: Boolean, baritoneSelectionInverted: Boolean): Boolean {
		if (flattenMode == FlattenMode.None) return true

		if (flattenMode == FlattenMode.Staircase) {
			val up = pos.up()
			if ((blockState(up).isNotEmpty && (!baritoneSelection || (isInBaritoneSelection(up) == !baritoneSelectionInverted)))
				|| (blockState(up.east()).isNotEmpty && (!baritoneSelection || (isInBaritoneSelection(up.east()) == !baritoneSelectionInverted)))
				|| (blockState(up.south()).isNotEmpty && (!baritoneSelection || (isInBaritoneSelection(up.south()) == !baritoneSelectionInverted)))
				|| (blockState(up.west()).isNotEmpty && (!baritoneSelection || (isInBaritoneSelection(up.west()) == !baritoneSelectionInverted)))
				|| (blockState(up.north()).isNotEmpty && (!baritoneSelection || (isInBaritoneSelection(up.north()) == !baritoneSelectionInverted)))
			)  { return false }
		}

		val flattenY = player.y.ceilToInt()
		val playerPos = player.blockPos
		val flattenLevel =
			if (sneakLowersFlatten && player.isSneaking) flattenY - 1
			else flattenY

		if (!flattenMode.isSmart && pos.y < flattenLevel)
			return false

		if (pos == player.supportingBlockPos) return false

		val playerLookDir = player.horizontalFacing
		val smartFlattenDir =
			if (flattenMode == FlattenMode.Smart) playerLookDir
			else playerLookDir?.opposite

		if (pos.y >= flattenLevel) return true

		val zeroedPos = pos.add(-playerPos.x, -flattenY, -playerPos.z)

		return (zeroedPos.x < 0 && smartFlattenDir == Direction.EAST)
				|| (zeroedPos.z < 0 && smartFlattenDir == Direction.SOUTH)
				|| (zeroedPos.x > 0 && smartFlattenDir == Direction.WEST)
				|| (zeroedPos.z > 0 && smartFlattenDir == Direction.NORTH)
	}

	fun isInBaritoneSelection(pos: BlockPos) =
		BaritoneHandler.primary?.selectionManager?.selections?.any {
			val min = it.min()
			val max = it.max()
			pos.x >= min.x && pos.x <= max.x
					&& pos.y >= min.y && pos.y <= max.y
					&& pos.z >= min.z && pos.z <= max.z
		} ?: false

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