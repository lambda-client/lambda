package com.lambda.interaction.construction.context

import net.minecraft.block.BlockState

interface BuildContext {
    val distance: Double
    val expectedState: BlockState
//    val resultingPos: BlockPos
}
