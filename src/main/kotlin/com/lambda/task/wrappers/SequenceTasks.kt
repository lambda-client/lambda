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

class SequencedTask<R, R2>(
	private val first: Task<R>,
	private val nextGenerator: TaskSupplier<R, R2>,
) : Task<R2>() {
	override val name get() = "Chaining ${first.name}"
	var second: Task<R2>? = null

	override fun SafeContext.onStart() {
		first
			.onSuccess { result ->
				second = nextGenerator(this, result)
					.onSuccess { success(it) }
					.execute(this@SequencedTask)
			}
			.execute(this@SequencedTask)
	}
}

class OptionalSequencedTask<R, R2>(
	private val first: Task<R>,
	private val nextGenerator: TaskOrNullSupplier<R, R2>
) : Task<R2?>() {
	override val name get() = "Chaining ${first.name}"

	override fun SafeContext.onStart() {
		first
			.onSuccess { result ->
				nextGenerator(this, result)
					?.onSuccess { success(it) }
					?.execute(this@OptionalSequencedTask)
					?: success(null)
			}
			.execute(this@OptionalSequencedTask)
	}
}