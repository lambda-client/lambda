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

class RecoveryTask<R, R2>(
	private val inner: Task<R>,
	private val recoveryTaskSupplier: SafeContext.(Throwable) -> Task<R2>,
) : Task<R2?>() {
	override val name get() = inner.name

	override fun SafeContext.onStart() {
		inner
			.onSuccess { success(null) }
			.execute(this@RecoveryTask)
	}

	override fun onSubTaskFailure(subTask: Task<*>, cause: Throwable) {
		if (subTask == inner) {
			runSafe {
				recoveryTaskSupplier(this, cause)
					.onSuccess { success(it) }
					.execute(this@RecoveryTask)
			}
		} else super.onSubTaskFailure(subTask, cause)
	}
}

class OptionalRecoveryTask<R, R2>(
	private val inner: Task<R>,
	private val recoveryTaskSupplier: SafeContext.(Throwable) -> Task<R2>?,
) : Task<R2?>() {
	override val name get() = inner.name

	override fun SafeContext.onStart() {
		inner
			.onSuccess { success(null) }
			.execute(this@OptionalRecoveryTask)
	}

	override fun onSubTaskFailure(subTask: Task<*>, cause: Throwable) {
		if (subTask == inner) {
			runSafe {
				recoveryTaskSupplier(this, cause)
					?.onSuccess { success(it) }
					?.execute(this@OptionalRecoveryTask)
					?: super.onSubTaskFailure(subTask, cause)
			}
		} else super.onSubTaskFailure(subTask, cause)
	}
}