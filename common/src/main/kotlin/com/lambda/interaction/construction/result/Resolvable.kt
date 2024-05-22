package com.lambda.interaction.construction.result

import com.lambda.task.TaskChain

interface Resolvable {
    val resolve: TaskChain
}