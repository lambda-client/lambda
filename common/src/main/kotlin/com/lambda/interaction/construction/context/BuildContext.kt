package com.lambda.interaction.construction.context

import net.minecraft.block.BlockState
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos

interface BuildContext : ComparableContext {
    val distance: Double
    val expectedState: BlockState
    val checkedState: BlockState
    val hand: Hand
    val resultingPos: BlockPos
}
