package com.lambda.task.tasks

import com.lambda.context.SafeContext
import com.lambda.interaction.material.ContainerManager
import com.lambda.interaction.material.ContainerManager.findContainerWithSelection
import com.lambda.interaction.material.StackSelection
import com.lambda.task.Task
import com.lambda.task.TaskCha1nBuilder

class AcquireMaterial(
    val selection: StackSelection
) : Task<StackSelection>() {
    override fun SafeContext.onStart() {
        findContainerWithSelection(selection)?.let { container ->
            emptyTask().withSubTasks {
                container.prepare()
                container.withdraw(selection)
            }.onSuccess { _, _ ->
                success(selection)
            }
        } ?: throw ContainerManager.NoContainerFound(selection) // ToDo: Create crafting path
    }

    companion object {
        @TaskCha1nBuilder
        fun acquireStack(selection: StackSelection) =
            AcquireMaterial(selection)
    }
}