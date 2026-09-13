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
fun <R> actionTask(action: SafeContext.() -> R) =
	ActionTask(action)

@Ta5kBuilder
infix fun <R, R2> Task<R>.thenAction(action: SafeContext.(R) -> R2): Task<R2> =
	SequencedActionTask(this, action)

class ActionTask<R> @Ta5kBuilder internal constructor(
	private val action: SafeContext.() -> R
) : Task<R>() {
	override val name get() = "Performing action"

	override fun SafeContext.onStart() {
		success(action())
	}
}

/**
 * A task that performs a given task ([innerTask]) and then a given [action].
 *
 * Useful when an action doesn't require an entire task created for it but still needs to be performed within the task branch.
 *
 * @see thenAction
 */
class SequencedActionTask<R, R2> @Ta5kBuilder internal constructor(
	private val innerTask: Task<R>,
	private val action: SafeContext.(R) -> R2,
) : Task<R2>() {
	override val name get() = "Performing action after ${innerTask.name}"

	override fun SafeContext.onStart() {
		innerTask
			.onSuccess { result ->
				success(action(this, result))
			}
			.start()
	}
}