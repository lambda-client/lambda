/*
 * Copyright 2025 Lambda
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

package com.lambda.util.world

import net.minecraft.block.Blocks
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.EightWayDirection

object StructureUtils {
	val PORTAL = setOf(
		BlockPos(0, 0, 0),
		BlockPos(0, 1, 0),
		BlockPos(0, 2, 0),
		BlockPos(0, 3, 0),
		BlockPos(1, 3, 0),
		BlockPos(2, 3, 0),
		BlockPos(3, 3, 0),
		BlockPos(1, -1, 0),
		BlockPos(2, -1, 0),
		BlockPos(3, 0, 0),
		BlockPos(3, 1, 0),
		BlockPos(3, 2, 0),
		BlockPos(3, 3, 0),
	)
	val LIGHT_UP = mapOf(
		BlockPos(1, 0, 0) to Blocks.FIRE.defaultState,
	)

	fun generateDirectionalTube(
		direction: EightWayDirection,
		width: Int,
		height: Int,
		leftRightOffset: Int,
		heightOffset: Int,
	): Set<BlockPos> {
		val tube = mutableSetOf<BlockPos>()

		val offsetX = leftRightOffset * direction.offsetX
		val offsetZ = leftRightOffset * direction.offsetZ

		(heightOffset until heightOffset + height).forEach { y ->
			(0 until width).forEach { x ->
				(0 until width).forEach { z ->
					tube.add(
						BlockPos(
							offsetX + x * direction.offsetX,
							y,
							offsetZ + z * direction.offsetZ,
						),
					)
				}
			}
		}

		return tube
	}
}
