package com.lambda.interaction.material.transfer

import com.lambda.task.TaskChain

sealed class TransferResult {
    data class Success(
        val taskChain: TaskChain
    ) : TransferResult()
    data object NoSpace : TransferResult() // ToDo: Needs inventory space resolver. compressing or disposing
    data class MissingItems(val missing: Int) : TransferResult() // ToDo: Find other satisfying permutations
}