package com.lambda.interaction.material.transfer

import com.lambda.task.Task

abstract class TransferResult {
    abstract val solve: Task<*>

    data class Success(
        override val solve: Task<*>
    ) : TransferResult()

    data object NoSpace : TransferResult() {
        // ToDo: Needs inventory space resolver. compressing or disposing
        override val solve = Task.emptyTask("NoSpace")
    }

    data class MissingItems(val missing: Int) : TransferResult() {
        // ToDo: Find other satisfying permutations
        override val solve = Task.emptyTask("MissingItems")
    }
}