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

package com.lambda.interaction.construction.result

import com.lambda.context.SafeContext
import com.lambda.graphics.renderer.esp.DirectionMask
import com.lambda.graphics.renderer.esp.DirectionMask.include
import com.lambda.util.BlockUtils.blockState
import net.minecraft.block.BlockState
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.shape.VoxelShape
import java.awt.Color

interface Drawable {
    fun SafeContext.buildRenderer()

    fun SafeContext.withBox(box: Box, color: Color, mask: Int = DirectionMask.ALL) {
        //StaticESP.filled(box, color, mask)
        //StaticESP.buildOutline(box, color, mask)
    }

    fun SafeContext.withState(blockState: BlockState, blockPos: BlockPos, color: Color, side: Direction) {
        withState(blockState, blockPos, color, DirectionMask.NONE.include(side))
    }

    fun SafeContext.withState(blockState: BlockState, blockPos: BlockPos, color: Color, mask: Int = DirectionMask.ALL) {
        withShape(blockState.getOutlineShape(world, blockPos), blockPos, color, mask)
    }

    fun SafeContext.withPos(blockPos: BlockPos, color: Color, side: Direction) {
        withPos(blockPos, color, DirectionMask.NONE.include(side))
    }

    fun SafeContext.withPos(blockPos: BlockPos, color: Color, mask: Int = DirectionMask.ALL) {
        val shape = blockState(blockPos).getOutlineShape(world, blockPos)
        withShape(shape, blockPos, color, mask)
    }

    fun SafeContext.withShape(shape: VoxelShape, offset: BlockPos, color: Color, mask: Int = DirectionMask.ALL) {
        if (shape.isEmpty) {
            withBox(Box(offset), color, mask)
            return
        }
        shape.boundingBoxes.forEach { box ->
            withBox(box.offset(offset), color, mask)
        }
    }
}
