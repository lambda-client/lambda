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

package com.lambda.util

import com.lambda.util.math.MathUtils.floorToInt
import net.minecraft.entity.Entity
import net.minecraft.util.math.BlockPos

object EntityUtils {
	fun Entity.getPositionsWithinHitboxXZ(minY: Int, maxY: Int): Set<BlockPos> {
		val hitbox = boundingBox
		val minX = hitbox.minX.floorToInt()
		val maxX = hitbox.maxX.floorToInt()
		val minZ = hitbox.minZ.floorToInt()
		val maxZ = hitbox.maxZ.floorToInt()
		val positions = mutableSetOf<BlockPos>()
		(minX..maxX).forEach { x ->
			(minY..maxY).forEach { y ->
				(minZ..maxZ).forEach { z ->
					positions.add(BlockPos(x, y, z))
				}
			}
		}
		return positions
	}
}