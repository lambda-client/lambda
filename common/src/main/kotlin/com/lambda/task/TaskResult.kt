/*
 * Copyright 2024 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

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
