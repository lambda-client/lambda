package com.lambda.client.util.schematic

import net.minecraft.block.state.IBlockState
import net.minecraft.util.math.BlockPos

interface Schematic {
    fun desiredState(x: Int, y: Int, z: Int): IBlockState {
        return desiredState(BlockPos(x, y, z))
    }

    fun desiredState(pos: BlockPos): IBlockState

    fun inSchematic(pos: BlockPos): Boolean
    fun inSchematic(x: Int, y: Int, z: Int): Boolean

    fun widthX(): Int
    fun heightY(): Int
    fun lengthZ(): Int
    fun getOrigin(): BlockPos
}