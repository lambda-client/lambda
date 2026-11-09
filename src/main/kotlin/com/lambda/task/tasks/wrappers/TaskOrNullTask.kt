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
import com.lambda.task.TaskMarker
import com.lambda.task.TaskOrNullSupplier

@TaskMarker
fun <R> taskOrNull(supplier: TaskOrNullSupplier<R>) = TaskOrNullTask(supplier)

/**
 * A task that runs an optional task, supplied by [taskOrNullSupplier].
 *
 * Useful for starting a task branch where the first task could or could not be skipped.
 *
 * @see taskOrNull
 */
class TaskOrNullTask<R> @TaskMarker internal constructor(
	private val taskOrNullSupplier: TaskOrNullSupplier<R>
) : Task<R?>() {
	override val name get() = "Optional task"
	override val isInvisible = true

	override fun SafeContext.onStart() {
		taskOrNullSupplier()
			?.onSuccess { success(it) }
			?.start()
			?: success(null)
	}
}