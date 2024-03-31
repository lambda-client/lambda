package com.lambda.task.tasks

import com.lambda.task.Task
import com.lambda.task.TaskCha1nBuilder
import com.lambda.task.TaskChainBuilder
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos

class PlaceContainer(
    val stack: ItemStack,
) : Task<BlockPos>() {
    override suspend fun onAction(): BlockPos {
        TODO("Not yet implemented")
    }

    companion object {
        @TaskCha1nBuilder
        fun TaskChainBuilder.placeContainer(stack: ItemStack) =
            PlaceContainer(stack).apply {
                required(this)
            }
    }
}