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