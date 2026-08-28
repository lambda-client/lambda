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
fun <R> successTask(result: R) =
	SuccessTask(result)

@Ta5kBuilder
fun successTask() = successTask(Unit)

@Ta5kBuilder
fun <R : Throwable> failureTask(throwable: R) =
	FailureTask(throwable)

@Ta5kBuilder
fun failureTask(cause: String) =
	FailureTask(IllegalStateException(cause))

class SuccessTask<R> @Ta5kBuilder internal constructor(
	val result: R
) : Task<R>() {
	override val name = "Success task"

	override fun SafeContext.onStart() {
		success(result)
	}
}

class FailureTask<R : Throwable> @Ta5kBuilder internal constructor(
	val throwable: R
) : Task<R>() {
	override val name = "Failure task"

	override fun SafeContext.onStart() {
		failure(throwable)
	}
}