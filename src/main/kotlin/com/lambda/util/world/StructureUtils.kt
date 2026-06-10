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

package com.lambda.util.world

import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.EightWayDirection

object StructureUtils {
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
