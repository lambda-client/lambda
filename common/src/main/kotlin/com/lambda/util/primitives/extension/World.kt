package com.lambda.util.primitives.extension

import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.fluid.FluidState
import net.minecraft.fluid.Fluids
import net.minecraft.world.World

fun World.getBlockState(x: Int, y: Int, z: Int): BlockState {
    if (isOutOfHeightLimit(y)) return Blocks.VOID_AIR.defaultState

    val chunk = getChunk(x shr 4, z shr 4)
    val sectionIndex = getSectionIndex(y)
    if (sectionIndex < 0) return Blocks.VOID_AIR.defaultState

    val section = chunk.getSection(sectionIndex)
    return section.getBlockState(x and 0xF, y and 0xF, z and 0xF)
}

fun World.getFluidState(x: Int, y: Int, z: Int): FluidState {
    if (isOutOfHeightLimit(y)) return Fluids.EMPTY.defaultState

    val chunk = getChunk(x shr 4, z shr 4)
    val sectionIndex = getSectionIndex(y)
    if (sectionIndex < 0) return Fluids.EMPTY.defaultState

    val section = chunk.getSection(sectionIndex)
    return section.getFluidState(x and 0xF, y and 0xF, z and 0xF)
}
