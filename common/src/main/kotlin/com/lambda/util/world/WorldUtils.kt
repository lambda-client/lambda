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

import com.lambda.context.SafeContext
import com.lambda.interaction.construction.simulation.Simulation.Companion.playerBox
import com.lambda.util.BlockUtils.blockState
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d

object WorldUtils {
    fun SafeContext.traversable(from: BlockPos, to: BlockPos) =
        BlockPos.stream(from, to).allMatch { traversable(it) }

    fun SafeContext.traversable(pos: BlockPos) =
        blockState(pos.down()).isSideSolidFullSquare(world, pos.down(), Direction.UP) && playerFitsIn(pos)

    fun SafeContext.playerFitsIn(pos: BlockPos) =
        world.isSpaceEmpty(Vec3d.ofBottomCenter(pos).playerBox())
}