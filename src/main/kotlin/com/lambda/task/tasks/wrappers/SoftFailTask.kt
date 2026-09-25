/*
 * Copyright 2026 Lambda
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

package com.lambda.task.tasks.wrappers

import com.lambda.context.SafeContext
import com.lambda.task.Task
import com.lambda.task.Task.Ta5kBuilder

@Ta5kBuilder
fun <R> Task<R>.softFail(): Task<R?> =
    SoftFailTask(this)

/**
 * A task that wraps another task ([innerTask]) and prevents failures from the [innerTask] from propagating.
 *
 * Useful for if you want to keep the task branch going even in the event one task fails.
 *
 * @see softFail
 */
class SoftFailTask<R> @Ta5kBuilder internal constructor(
    private val innerTask: Task<R>,
) : Task<R?>() {
    override val name get() = "Soft fail protection for ${innerTask.name}"

    override fun SafeContext.onStart() {
        innerTask
            .onSuccess { success(it) }
            .execute(this@SoftFailTask)
    }

    override fun onSubTaskFailure(subTask: Task<*>, cause: Throwable) {
        if (subTask == innerTask) success(null)
        else super.onSubTaskFailure(subTask, cause)
    }
}
