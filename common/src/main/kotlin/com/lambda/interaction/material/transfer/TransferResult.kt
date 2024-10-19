package com.lambda.interaction.material.transfer

import com.lambda.context.SafeContext
import com.lambda.interaction.material.MaterialContainer
import com.lambda.interaction.material.StackSelection
import com.lambda.task.Task

abstract class TransferResult : Task<Unit>() {
    data class Transfer(
        val selection: StackSelection,
        val from: MaterialContainer,
        val to: MaterialContainer
    ) : TransferResult() {
        override fun SafeContext.onStart() {
            from.withdraw(selection).thenRun(this@Transfer) { _, _ ->
                to.deposit(selection).onSuccess { _, _ ->
                    success(Unit)
                }
            }.start(this@Transfer)
        }

        override fun toString() = "Transfer of [$selection] from [${from.name}] to [${to.name}]"
    }

    data object NoSpace : TransferResult() {
        // ToDo: Needs inventory space resolver. compressing or disposing
        override fun SafeContext.onStart() {
            failure("No space left in the target container")
        }
    }

    data class MissingItems(val missing: Int) : TransferResult() {
        // ToDo: Find other satisfying permutations
        override fun SafeContext.onStart() {
            failure("Missing $missing items")
        }
    }
}