package com.lambda.task.tasks

import com.lambda.context.SafeContext
import com.lambda.interaction.material.ContainerManager
import com.lambda.interaction.material.ContainerManager.findContainerWithSelection
import com.lambda.interaction.material.StackSelection
import com.lambda.task.Task

class AcquireMaterial(
    val selection: StackSelection
) : Task<StackSelection>() {
    override fun SafeContext.onStart() {
        findContainerWithSelection(selection)
            ?.doWithdrawal(selection)
            ?.onSuccess { _, _ ->
                success(selection)
            }?.start(this@AcquireMaterial)
            ?: failure(ContainerManager.NoContainerFound(selection)) // ToDo: Create crafting path
    }

    companion object {
        @Ta5kBuilder
        fun acquire(selection: () -> StackSelection) =
            AcquireMaterial(selection())
    }
}