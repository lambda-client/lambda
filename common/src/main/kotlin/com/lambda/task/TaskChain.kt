package com.lambda.task

import com.lambda.util.Communication.info

class TaskChain(
    private val steps: List<Task<*>> = listOf()
) {
    operator fun plus(other: TaskChain) = TaskChain(steps + other.steps)

    suspend fun execute() {
//        info("Executing TaskChain $this")
        steps.forEach {
            info("Executing task: ${it.name}")
            it.execute()
        }
    }

    override fun toString() = "TaskChain:\n${steps.withIndex().joinToString("\n") { "${it.index + 1}. ${it.value}" }}"
}
