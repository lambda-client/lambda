package com.lambda.task

sealed class TaskResult<out Result> {
    data class Success<out T>(val value: T) : TaskResult<T>()
    data class Failure(val throwable: Throwable) : TaskResult<Nothing>()
    data class Timeout(val timeout: Long, val triesUsed: Int) : TaskResult<Nothing>()
    data object Cancelled : TaskResult<Nothing>()
}