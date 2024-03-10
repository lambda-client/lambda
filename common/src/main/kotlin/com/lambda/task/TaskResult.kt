package com.lambda.task

/**
 * Represents the result of a task.
 *
 * A task result can be successful, failed, timed out, or canceled.
 *
 * @param Result The type of the result value for successful tasks. Covariant type parameter.
 */
sealed class TaskResult<out Result> {

    /**
     * Represents a successful task result.
     *
     * @param T The type of the result value.
     * @property value The result value.
     */
    data class Success<out T>(val value: T) : TaskResult<T>()

    /**
     * Represents a failed task result.
     *
     * @property throwable The exception that caused the task to fail.
     */
    data class Failure(val throwable: Throwable) : TaskResult<Nothing>()

    /**
     * Represents a task result that timed out.
     *
     * @property timeout The timeout duration in milliseconds.
     * @property triesUsed The number of attempts made before the task timed out.
     */
    data class Timeout(val timeout: Long, val triesUsed: Int) : TaskResult<Nothing>()

    /**
     * Represents a cancelled task result.
     */
    data object Cancelled : TaskResult<Nothing>()
}