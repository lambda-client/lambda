package com.lambda.interaction.construction.result

import com.lambda.task.Task

interface Resolvable {
    val resolve: Task<*>
}