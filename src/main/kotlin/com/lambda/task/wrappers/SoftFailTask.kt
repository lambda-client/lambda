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

typealias TaskSupplier<R, R2> = SafeContext.(R) -> Task<R2>
typealias TaskOrNullSupplier<R, R2> = SafeContext.(R) -> Task<R2>?

@Ta5kBuilder
fun <R> Task<R>.softFail(): Task<R?> =
    SoftFailTask(this)

class SoftFailTask<R>(
    private val inner: Task<R>,
) : Task<R?>() {
    override val name get() = inner.name

    override fun SafeContext.onStart() {
        inner
            .onSuccess { success(it) }
            .execute(this@SoftFailTask)
    }

    override fun onSubTaskFailure(subTask: Task<*>, cause: Throwable) {
        if (subTask == inner) success(null)
        else super.onSubTaskFailure(subTask, cause)
    }
}
