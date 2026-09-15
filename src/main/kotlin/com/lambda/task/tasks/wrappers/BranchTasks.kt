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

@Ta5kBuilder
fun <R, R2> Task<R>.withBranch(
	onSuccess: TaskGenerator<R, R2>,
	onFailure: TaskGenerator<Throwable, R2>
) = BranchTask(this, onSuccess, onFailure)

@Ta5kBuilder
fun <R, R2> Task<R>.withBranchOrNull(
	onSuccess: TaskOrNullGenerator<R, R2>,
	onFailure: TaskOrNullGenerator<Throwable, R2>
) = OptionalBranchTask(this, onSuccess, onFailure)

/**
 * A task that strictly branches into one of two tasks based on the success or failure of the [innerTask].
 *
 * @see withBranch
 */
class BranchTask<R, R2> @Ta5kBuilder internal constructor(
	private val innerTask: Task<R>,
	private val onSuccessGenerator: TaskGenerator<R, R2>,
	private val onFailureGenerator: TaskGenerator<Throwable, R2>
) : Task<R2>() {
	override val name get() = "Branching task for ${innerTask.name}"

	override fun SafeContext.onStart() {
		innerTask
			.onSuccess { result ->
				onSuccessGenerator(this, result)
					.onSuccess { success(it) }
					.execute(this@BranchTask)
			}
			.onFailure { cause ->
				onFailureGenerator(this, cause)
					.onSuccess { success(it) }
					.execute(this@BranchTask)
			}
			.execute(this@BranchTask)
	}

	override fun onSubTaskFailure(subTask: Task<*>, cause: Throwable) {
		if (subTask != innerTask) super.onSubTaskFailure(subTask, cause)
	}
}

/**
 * A task that strictly branches into one of two optional tasks based on the success or failure of the [innerTask].
 *
 * @see withBranchOrNull
 */
class OptionalBranchTask<R, R2> @Ta5kBuilder internal constructor(
	private val innerTask: Task<R>,
	private val onSuccessGenerator: TaskOrNullGenerator<R, R2>,
	private val onFailureGenerator: TaskOrNullGenerator<Throwable, R2>
) : Task<R2?>() {
	override val name get() = "Optional branching task for ${innerTask.name}"

	override fun SafeContext.onStart() {
		innerTask
			.onSuccess { result ->
				onSuccessGenerator(this, result)
					?.onSuccess { success(it) }
					?.execute(this@OptionalBranchTask)
					?: success(null)
			}
			.onFailure { cause ->
				onFailureGenerator(this, cause)
					?.onSuccess { success(it) }
					?.execute(this@OptionalBranchTask)
					?: failure("Optional onFailure task not present for task: ${innerTask.name}")
			}
			.execute(this@OptionalBranchTask)
	}

	override fun onSubTaskFailure(subTask: Task<*>, cause: Throwable) {
		if (subTask != innerTask) super.onSubTaskFailure(subTask, cause)
	}
}

