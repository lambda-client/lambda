package com.lambda.task.tasks

import com.lambda.interaction.material.ContainerManager
import com.lambda.interaction.material.ContainerManager.findContainerWithSelection
import com.lambda.interaction.material.StackSelection
import com.lambda.task.Task
import com.lambda.task.TaskCha1nBuilder
import com.lambda.task.TaskChainBuilder
import net.minecraft.item.ItemStack

class AcquireMaterial(
    val selection: StackSelection
) : Task<StackSelection>() {
    override suspend fun onAction(): StackSelection {
        // Find the best source with enough supplies
        findContainerWithSelection(selection)?.let { container ->
            (container.prepare() + container.withdraw(selection)).execute()
        } ?: throw ContainerManager.NoContainerFound(selection) // ToDo: Create crafting path

        return selection
    }

    companion object {
        @TaskCha1nBuilder
        fun TaskChainBuilder.acquireStack(selection: StackSelection) =
            AcquireMaterial(selection).apply {
                required(this)
            }
    }
}