package com.lambda.task.tasks

import com.lambda.task.Task
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos

class PlaceContainer(
    val stack: ItemStack,
) : Task<BlockPos>() {

    companion object {
        @Ta5kBuilder
        fun placeContainer(stack: ItemStack) =
            PlaceContainer(stack)
    }
}