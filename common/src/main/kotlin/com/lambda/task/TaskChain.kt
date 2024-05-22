package com.lambda.task

import kotlinx.coroutines.ExperimentalCoroutinesApi

class TaskChain(
    val steps: List<Task<*>> = listOf()
) {
    suspend fun run() {
        steps.forEach { TaskRegistry.run(it) }
    }

    fun tryRun() {
        steps.forEach { TaskRegistry.tryRun(it) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun cancel() {
        TaskRegistry.taskFlow.resetReplayCache()
        TaskRegistry.registry.keys
            .filter { it in steps }
            .forEach { TaskRegistry.cancel(it) }
    }

    operator fun plus(other: TaskChain) = TaskChain(steps + other.steps)

    override fun toString() = "TaskChain:\n${steps.withIndex().joinToString("\n") { "${it.index + 1}. ${it.value}" }}"
}
