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
import com.lambda.task.TaskGenerator
import com.lambda.task.TaskOrNullGenerator
import com.lambda.task.TaskOrNullSupplier

@Ta5kBuilder
infix fun <R, R2> Task<R>.then(generator: TaskGenerator<R, R2>) =
	SequencedTask(this, generator)

@Ta5kBuilder
infix fun <R, R2> Task<R>.then(task: Task<R2>): Task<R2> =
	SequencedTask(this) { task }

@Ta5kBuilder
fun <R> Task<R>.then(vararg tasks: Task<*>) =
	tasks.fold<Task<*>, Task<*>>(this) { acc, task ->
		acc then task
	}

@Ta5kBuilder
infix fun <R, R2> Task<R>.thenOrNull(generator: TaskOrNullGenerator<R, R2>) =
	OptionalSequencedTask(this, generator)

@Ta5kBuilder
fun <R> taskOrNull(supplier: TaskOrNullSupplier<R>) = TaskOrNullTask(supplier)

/**
 * A task that sequences the [firstTask] with the next task, supplied by [nextTaskGenerator].
 *
 * Useful for when two tasks must be run sequentially. The result of the first task is fed into the supplier for the second.
 *
 * @see then
 */
class SequencedTask<R, R2> @Ta5kBuilder internal constructor(
	private val firstTask: Task<R>,
	private val nextTaskGenerator: TaskGenerator<R, R2>,
) : Task<R2>() {
	override val name get() = "Chaining ${firstTask.name}"
	var second: Task<R2>? = null

	override fun SafeContext.onStart() {
		firstTask
			.onSuccess { result ->
				second = nextTaskGenerator(this, result)
					.onSuccess { success(it) }
					.execute(this@SequencedTask)
			}
			.execute(this@SequencedTask)
	}
}

/**
 * A task that sequences the [firstTask] with the next optional task, supplied by [nextTaskGenerator].
 *
 * Useful for when you want to sequence a second task after the first based on a predicate. The result of the first task could help shape that outcome.
 *
 * @see thenOrNull
 */
class OptionalSequencedTask<R, R2> @Ta5kBuilder internal constructor(
	private val firstTask: Task<R>,
	private val nextTaskGenerator: TaskOrNullGenerator<R, R2>
) : Task<R2?>() {
	override val name get() = "Chaining ${firstTask.name}"

	override fun SafeContext.onStart() {
		firstTask
			.onSuccess { result ->
				nextTaskGenerator(this, result)
					?.onSuccess { success(it) }
					?.execute(this@OptionalSequencedTask)
					?: success(null)
			}
			.execute(this@OptionalSequencedTask)
	}
}

/**
 * A task that runs an optional task, supplied by [taskOrNullSupplier].
 *
 * Useful for starting a task branch where the first task could or could not be skipped.
 *
 * @see taskOrNull
 */
class TaskOrNullTask<R> @Ta5kBuilder internal constructor(
	private val taskOrNullSupplier: TaskOrNullSupplier<R>
) : Task<R?>() {
	override val name get() = "Optional task"

	override fun SafeContext.onStart() {
		taskOrNullSupplier()
			?.onSuccess { success(it) }
			?.execute(this@TaskOrNullTask)
			?: success(null)
	}
}