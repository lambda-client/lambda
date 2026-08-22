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

package com.lambda.task

import com.lambda.context.SafeContext
import com.lambda.task.Task.Ta5kBuilder
import com.lambda.threading.runSafe

typealias TaskSupplier<R, R2> = SafeContext.(R) -> Task<R2>
typealias TaskOrNullSupplier<R, R2> = SafeContext.(R) -> Task<R2>?

@Ta5kBuilder
infix fun <R, R2> Task<R>.then(supplier: TaskSupplier<R, R2>) =
    SequencedTask(this, supplier)

@Ta5kBuilder
infix fun <R, R2> Task<R>.then(task: Task<R2>): Task<R2> {
    require(task != this) { "Cannot link a task to itself" }
    return SequencedTask(this) { task }
}

@Ta5kBuilder
fun <R, R2> Task<R>.then(vararg tasks: Task<R2>) =
	tasks.fold<Task<*>, Task<*>>(this) { acc, task ->
		acc then task
	}

@Ta5kBuilder
fun <R, R2> Task<R>.thenOrNull(supplier: SafeContext.(R) -> Task<R2>?) =
    OptionalSequencedTask(this, supplier)

@Ta5kBuilder
fun <R> Task<R>.thenAction(action: SafeContext.(R) -> Unit): Task<R> =
    SequencedActionTask(this, action)

@Ta5kBuilder
fun <R, R2> Task<R>.onFail(handler: SafeContext.(Throwable) -> Task<R2>) =
    RecoveryTask(this, handler)

@Ta5kBuilder
fun <R, R2> Task<R>.onFailOrNull(handler: SafeContext.(Throwable) -> Task<R2>?) =
    OptionalRecoveryTask(this, handler)

@Ta5kBuilder
fun <R> Task<R>.softFail(): Task<R?> =
    SoftFailTask(this)

//@Ta5kBuilder
//fun taskChain(
//    name: String,
//    builder: TaskChainScope.() -> Unit
//): TaskChainTask {
//    val scope = TaskChainScope()
//    scope.builder()
//    return TaskChainTask(name, scope.steps)
//}
//
//class TaskChainScope {
//    internal val steps = mutableListOf<ChainStep>()
//
//    fun then(generator: SafeContext.() -> Task<*>) {
//        steps.add(ChainStep(required = true) { generator(it) })
//    }
//
//    fun thenOrNull(generator: SafeContext.() -> Task<*>?) {
//        steps.add(ChainStep(required = false) { generator(it) })
//    }
//}

//data class ChainStep(
//    val required: Boolean,
//    val generator: (SafeContext) -> Task<*>?,
//)

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

class SequencedActionTask<R>(
    private val inner: Task<R>,
    private val action: SafeContext.(R) -> Unit,
) : Task<R>() {
    override val name get() = inner.name

    override fun SafeContext.onStart() {
        inner
            .onSuccess { result ->
                action(this, result)
                success(result)
            }
            .execute(this@SequencedActionTask)
    }
}

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

//class TaskChainTask(
//    override val name: String,
//    private val steps: List<ChainStep>,
//) : Task<Unit>() {
//    private var currentIndex = 0
//
//    override fun SafeContext.onStart() {
//        advanceToNextStep()
//    }
//
//    private fun SafeContext.advanceToNextStep() {
//        while (currentIndex < steps.size) {
//            val step = steps[currentIndex++]
//            val task = step.generator(this)
//
//            if (task != null) {
//                task.execute(this@TaskChainTask, pauseParent = true)
//                return
//            }
//
//            if (step.required) {
//                failure("Required chain step returned null")
//                return
//            }
//        }
//
//        success(Unit)
//    }
//
//    override fun onSubTaskCompletion(subTask: Task<*>) {
//        runSafe { advanceToNextStep() }
//    }
//}
