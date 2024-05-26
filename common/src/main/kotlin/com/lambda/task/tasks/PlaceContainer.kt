package com.lambda.task.tasks

import com.lambda.task.Task
import com.lambda.task.TaskCha1nBuilder
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos

class PlaceContainer(
    val stack: ItemStack,
) : Task<BlockPos>() {

    companion object {
        @TaskCha1nBuilder
        fun placeContainer(stack: ItemStack) =
            PlaceContainer(stack)
    }
}