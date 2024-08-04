package com.lambda.task.tasks

import com.lambda.context.SafeContext
import com.lambda.interaction.material.MaterialContainer
import com.lambda.interaction.material.StackSelection
import com.lambda.task.Task

class ContainerTransfer @Ta5kBuilder constructor(
    val selection: StackSelection,
    val from: MaterialContainer,
    val to: MaterialContainer
) : Task<Unit>() {
    override fun SafeContext.onStart() {
        from.withdraw(selection).thenRun(this@ContainerTransfer) { _, _ ->
            to.deposit(selection).onSuccess { _, _ ->
                success(Unit)
            }
        }.start(this@ContainerTransfer)
    }
}