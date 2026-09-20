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

package com.lambda.task.wrappers

import com.lambda.context.SafeContext
import com.lambda.task.Task
import com.lambda.task.Task.Ta5kBuilder

@Ta5kBuilder
infix fun <R, R2> Task<R>.then(supplier: TaskSupplier<R, R2>) =
	SequencedTask(this, supplier)

@Ta5kBuilder
infix fun <R, R2> Task<R>.then(task: Task<R2>): Task<R2> {
	require(task != this) { "Cannot link a task to itself" }
	return SequencedTask(this) { task }
}

@Ta5kBuilder
fun <R> Task<R>.then(vararg tasks: Task<*>) =
	tasks.fold<Task<*>, Task<*>>(this) { acc, task ->
		acc then task
	}

@Ta5kBuilder
infix fun <R, R2> Task<R>.thenOrNull(supplier: SafeContext.(R) -> Task<R2>?) =
	OptionalSequencedTask(this, supplier)

/**
 * A task that sequences the [firstTask] with the next task, supplied by [nextTaskSupplier].
 *
 * Useful for when two tasks must be run sequentially. The result of the first task is fed into the supplier for the second.
 *
 * @see then
 */
class SequencedTask<R, R2>(
	private val firstTask: Task<R>,
	private val nextTaskSupplier: TaskSupplier<R, R2>,
) : Task<R2>() {
	override val name get() = "Chaining ${firstTask.name}"
	var second: Task<R2>? = null

	override fun SafeContext.onStart() {
		firstTask
			.onSuccess { result ->
				second = nextTaskSupplier(this, result)
					.onSuccess { success(it) }
					.execute(this@SequencedTask)
			}
			.execute(this@SequencedTask)
	}
}

/**
 * A task that sequences the [firstTask] with the next optional task, supplied by [nextTaskSupplier].
 *
 * Useful for when you want to sequence a second task after the first based on a predicate. The result of the first task could help shape that outcome.
 *
 * @see thenOrNull
 */
class OptionalSequencedTask<R, R2>(
	private val firstTask: Task<R>,
	private val nextTaskSupplier: TaskOrNullSupplier<R, R2>
) : Task<R2?>() {
	override val name get() = "Chaining ${firstTask.name}"

	override fun SafeContext.onStart() {
		firstTask
			.onSuccess { result ->
				nextTaskSupplier(this, result)
					?.onSuccess { success(it) }
					?.execute(this@OptionalSequencedTask)
					?: success(null)
			}
			.execute(this@OptionalSequencedTask)
	}
}