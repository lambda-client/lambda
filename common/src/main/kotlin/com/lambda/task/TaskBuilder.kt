package com.lambda.task

import com.lambda.task.OptionalTask.Companion.toOptionalTask
import com.lambda.task.Task.Companion.toTask

@DslMarker
annotation class TaskCha1nBuilder // Name is used to force the dsl style turquoise (name hash)

@DslMarker
annotation class Ta5kBuilder // Name is used to force the dsl style yellow (name hash)

@TaskCha1nBuilder
fun emptyChain() = TaskChain()

@TaskCha1nBuilder
fun buildTask(
    block: suspend () -> Unit
) = TaskChain(listOf(block.toTask()))

@TaskCha1nBuilder
fun buildChain(
    block: TaskChainBuilder.() -> Unit
) = TaskChainBuilder().apply {
    block()
}.build()

@TaskCha1nBuilder
class TaskChainBuilder {
    private val steps: MutableList<Task<*>> = mutableListOf()

    @TaskCha1nBuilder
    fun required(block: suspend () -> Unit) {
        steps.add(block.toTask())
    }

    @TaskCha1nBuilder
    fun required(task: Task<*>) {
        steps.add(task)
    }

    @TaskCha1nBuilder
    fun optional(block: suspend () -> Unit) {
        // ToDo: Find application to tell design. Should be based on a task strategy
        steps.add(block.toOptionalTask {
            true
        })
    }

    @TaskCha1nBuilder
    fun optional(task: OptionalTask<*>) {
        steps.add(task)
    }

    fun build() = TaskChain(steps)
}