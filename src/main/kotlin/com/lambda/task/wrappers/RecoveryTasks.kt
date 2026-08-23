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
import com.lambda.threading.runSafe

@Ta5kBuilder
infix fun <R, R2> Task<R>.onFail(handler: SafeContext.(Throwable) -> Task<R2>) =
	RecoveryTask(this, handler)

@Ta5kBuilder
infix fun <R, R2> Task<R>.onFailOrNull(handler: SafeContext.(Throwable) -> Task<R2>?) =
	OptionalRecoveryTask(this, handler)

/**
 * A task that performs a given task ([innerTask]) with a fallback to the task supplied by [recoveryTaskSupplier] in-case of failure.
 *
 * Useful for if you want to keep the task branch going even in the event an individual task fails.
 *
 * @see onFail
 */
class RecoveryTask<R, R2>(
	private val innerTask: Task<R>,
	private val recoveryTaskSupplier: SafeContext.(Throwable) -> Task<R2>,
) : Task<R2?>() {
	override val name get() = "Recovery task in case of failure for ${innerTask.name}"

	override fun SafeContext.onStart() {
		innerTask
			.onSuccess { success(null) }
			.execute(this@RecoveryTask)
	}

	override fun onSubTaskFailure(subTask: Task<*>, cause: Throwable) {
		if (subTask == innerTask) {
			runSafe {
				recoveryTaskSupplier(this, cause)
					.onSuccess { success(it) }
					.execute(this@RecoveryTask)
			}
		} else super.onSubTaskFailure(subTask, cause)
	}
}

/**
 * A task that performs a given task ([innerTask]) with a fallback to the nullable task supplied by [recoveryTaskSupplier] in-case of failure.
 *
 * Useful for if you want to keep the task branch going even in the event an individual task fails, but want to supply the recovery task based on a predicate.
 *
 * @see onFailOrNull
 */
class OptionalRecoveryTask<R, R2>(
	private val innerTask: Task<R>,
	private val recoveryTaskSupplier: SafeContext.(Throwable) -> Task<R2>?,
) : Task<R2?>() {
	override val name get() = "Optional recovery task in case of failure for ${innerTask.name}"

	override fun SafeContext.onStart() {
		innerTask
			.onSuccess { success(null) }
			.execute(this@OptionalRecoveryTask)
	}

	override fun onSubTaskFailure(subTask: Task<*>, cause: Throwable) {
		if (subTask == innerTask) {
			runSafe {
				recoveryTaskSupplier(this, cause)
					?.onSuccess { success(it) }
					?.execute(this@OptionalRecoveryTask)
					?: super.onSubTaskFailure(subTask, cause)
			}
		} else super.onSubTaskFailure(subTask, cause)
	}
}