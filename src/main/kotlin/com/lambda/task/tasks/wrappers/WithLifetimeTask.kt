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
infix fun <R, R2> Task<R>.withLifetimeParent(task: TaskSupplier<R2>) =
	WithLifetimeTask(this, task)

@TaskMarker
infix fun <R, R2> Task<R>.withLifetimeParentOrNull(task: TaskOrNullSupplier<R2>) =
	OptionalWithLifetimeTask(this, task)

class WithLifetimeTask<R, R2> @TaskMarker internal constructor(
	private val task: Task<R>,
	private val parentTaskSupplier: TaskSupplier<R2>
) : Task<Pair<R?, R2?>>() {
	override val name = "Running ${task.name} within a given tasks lifetime"
	override val isInvisible = true

	override fun SafeContext.onStart() {
		parentTaskSupplier()
			.with { task.onSuccess { success(Pair(it, null)) } }
			.onSuccess { success(Pair(null, it)) }
			.start()
	}
}

class OptionalWithLifetimeTask<R, R2> @TaskMarker internal constructor(
	private val task: Task<R>,
	private val parentTaskSupplier: TaskOrNullSupplier<R2>
) : Task<Pair<R?, R2?>>() {
	override val name = "Running ${task.name} within a given optional tasks lifetime"
	override val isInvisible = true

	override fun SafeContext.onStart() {
		val parentTask = parentTaskSupplier()

		val task = task.onSuccess { success(Pair(it, null)) }

		val chain = parentTask
			?.with { task }
			?.onSuccess { success(Pair(null, it)) }
			?: task

		chain.start()
	}
}