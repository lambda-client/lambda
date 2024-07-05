package com.lambda.interaction.construction.result

import com.lambda.context.SafeContext
import com.lambda.graphics.renderer.esp.DirectionMask
import com.lambda.graphics.renderer.esp.DirectionMask.include
import com.lambda.graphics.renderer.esp.global.StaticESP
import com.lambda.graphics.renderer.esp.builders.buildFilled
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
        StaticESP.buildFilled(box, color, mask)
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
        val shape = blockPos.blockState(world).getOutlineShape(world, blockPos)
        withShape(shape, blockPos, color, mask)
    }

    fun SafeContext.withShape(shape: VoxelShape, offset: BlockPos, color: Color, mask: Int = DirectionMask.ALL) {
        if (shape.isEmpty) return
        shape.boundingBoxes.forEach { box ->
            withBox(box.offset(offset), color, mask)
        }
    }
}