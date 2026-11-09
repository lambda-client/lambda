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
import com.lambda.task.TaskSupplier

@TaskMarker
infix fun <R> Task<R>.with(sideTask: TaskSupplier<*>) = WithTask(this, sideTask)

@TaskMarker
infix fun <R> Task<R>.withOrNull(sideTask: TaskOrNullSupplier<*>) = OptionalWithTask(this, sideTask)

@TaskMarker
infix fun <R> Task<R>.withPausing(task: TaskSupplier<*>) = WithPausingTask(this, task)

@TaskMarker
infix fun <R> Task<R>.withPausingOrNull(task: TaskSupplier<*>) = OptionalWithPausingTask(this, task)

class WithTask<R> @TaskMarker internal constructor(
	private val task: Task<R>,
	private val sideTask: TaskSupplier<*>
) : Task<R>() {
	override val name = "Running ${task.name} with side task"
	override val isInvisible = true

	override fun SafeContext.onStart() {
		task
			.onSuccess { success(it) }
			.start()

		sideTask().execute(task, pauseParent = false)
	}
}

class OptionalWithTask<R> @TaskMarker internal constructor(
	private val task: Task<R>,
	private val sideTask: TaskOrNullSupplier<*>
) : Task<R>() {
	override val name = "Running ${task.name} with optional side task"
	override val isInvisible = true

	override fun SafeContext.onStart() {
		task
			.onSuccess { success(it) }
			.start()

		sideTask()?.execute(task, pauseParent = false)
	}
}

class WithPausingTask<R> @TaskMarker internal constructor(
	private val task: Task<R>,
	private val pausingTaskSupplier: TaskSupplier<*>
) : Task<R>() {
	override val name = "Running ${task.name} with pausing task"
	override val isInvisible = true

	override fun SafeContext.onStart() {
		task
			.onSuccess { success(it) }
			.start()

		pausingTaskSupplier().execute(task)
	}
}

class OptionalWithPausingTask<R> @TaskMarker internal constructor(
	private val task: Task<R>,
	private val pausingTaskSupplier: TaskOrNullSupplier<*>
) : Task<R>() {
	override val name = "Running ${task.name} with optional pausing task"
	override val isInvisible = true

	override fun SafeContext.onStart() {
		task
			.onSuccess { success(it) }
			.start()

		pausingTaskSupplier()?.execute(task)
	}
}