package com.lambda.interaction.material.transfer

import com.lambda.interaction.material.MaterialContainer
import com.lambda.interaction.material.StackSelection
import com.lambda.task.Task
import com.lambda.task.Task.Companion.failTask

abstract class TransferResult {
    abstract val solve: Task<*>

    data class Success(
        val selection: StackSelection,
        val from: MaterialContainer,
        val to: MaterialContainer
    ) : TransferResult() {
        override val solve: Task<*> = from.doWithdrawal(selection).onSuccess { withdraw, _ ->
            to.doDeposit(selection).start(withdraw)
        }

        val undo = to.doWithdrawal(selection).onSuccess { withdraw, _ ->
            from.doDeposit(selection).start(withdraw)
        }
    }

    data object NoSpace : TransferResult() {
        // ToDo: Needs inventory space resolver. compressing or disposing
        override val solve = failTask("NoSpace")
    }

    data class MissingItems(val missing: Int) : TransferResult() {
        // ToDo: Find other satisfying permutations
        override val solve = failTask("MissingItems")
    }
}