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

import com.lambda.Lambda.LOG
import com.lambda.context.SafeContext
import com.lambda.event.EventFlow.unsubscribe
import com.lambda.event.Muteable
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.module.modules.client.Client.verboseDebug
import com.lambda.task.wrappers.onFail
import com.lambda.threading.runSafe
import com.lambda.util.CommunicationUtils.logError
import com.lambda.util.Nameable
import com.lambda.util.StringUtils.capitalize
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@Suppress("unused")
abstract class Task<Result> : Nameable, Muteable {
    var parent: Task<*>? = null
    var parentPausing = false
    val subTasks = mutableListOf<Task<*>>()
    var state = State.Init
    override val isMuted: Boolean get() = state == State.Paused || state == State.Init
    var age = 0
    private val depth: Int get() = parent?.depth?.plus(1) ?: 0
    val isCompleted get() = state == State.Completed
    val size: Int get() = subTasks.sumOf { it.size } + 1

    private val successCallbacks = mutableListOf<SafeContext.(Result) -> Unit>()
    private val completionCallbacks = mutableListOf<SafeContext.() -> Unit>()
    private val failureCallbacks = mutableListOf<SafeContext.(Throwable) -> Unit>()

    val duration: String get() =
        (age * 50).toDuration(DurationUnit.MILLISECONDS).toComponents { days, hours, minutes, seconds, nanoseconds ->
            "${"%03d".format(days)}:${"%02d".format(hours)}:${"%02d".format(minutes)}:${"%02d".format(seconds)}.${"${nanoseconds / 1_000_000}".take(2)}"
        }

    enum class State {
        Init,
        Running,
        Paused,
        Cancelled,
        Failed,
        Completed;

        val display get() = name.lowercase().capitalize()
    }

    init {
        listen<TickEvent.Pre>({ Int.MAX_VALUE }) { age++ }
    }

    /**
     * "Typo" in name is used to force the dsl style green
     * (color is based on name hash, don't ask me who came up with this)
     */
    @DslMarker
    annotation class Ta5kBuilder

    /**
     * Invoked when the task starts execution.
     *
     * This method serves as a lifecycle hook and can be overridden to define
     * custom behavior or initialization steps necessary before the task begins.
     * It provides a `SafeContext` to access relevant context-sensitive properties
     * or actions safely.
     *
     * By default, this method does not contain any logic. Subclasses may override
     * it to implement specific functionality, such as logging, resource allocation,
     * or preparing preconditions for task execution.
     */
    @Ta5kBuilder
    protected open fun SafeContext.onStart() {}

    /**
     * This function is called when the task is canceled.
     * It can be overridden for tasks that need to perform cleanup operations,
     * such as cancelling a block breaking progress, releasing resources,
     * or stopping any ongoing operations that were started by the task.
     */
    @Ta5kBuilder
    protected open fun SafeContext.onCancel() {}

    /**
     * Executes the current task as a subtask of the specified owner task.
     *
     * This method adds the current task to the owner's subtasks, sets the parent relationship,
     * logs the execution details, and invokes the necessary lifecycle hooks. Additionally,
     * it manages the state of the parent task and starts any required listeners for execution.
     *
     * @param owner The parent task that will execute this task as a sub task. Must not be the same as this task.
     * @param pauseParent Defines whether the parent task should be paused during the execution of this task. Defaults to `true`.
     * @return The current task instance as a `Task<Result>` to support chaining or further configuration.
     * @throws IllegalArgumentException if the owner task is the same as the task being executed.
     */
    @Ta5kBuilder
    fun execute(owner: Task<*>, pauseParent: Boolean = true): Task<Result> {
        require(owner != this) { "Cannot execute a task as a sub task of itself" }
        owner.subTasks.add(this)
        parent = owner
        if (verboseDebug) LOG.info("${owner.name} started $name")
        if (pauseParent) {
            parentPausing = true
            if (verboseDebug) LOG.info("$name pausing parent ${owner.name}")
            if (owner !is RootTask) owner.pause()
        }
        state = State.Running
        runSafe { runCatching { onStart() }.onFailure { failure(it) } }
        return this
    }

    @Ta5kBuilder
    protected fun success(result: Result) {
        unsubscribe()
        state = State.Completed

        parent?.onSubTaskSuccess(this)
        parent?.onSubTaskCompletion(this)

        runSafe {
            successCallbacks.forEach { it.invoke(this, result) }
            completionCallbacks.forEach { it.invoke(this) }
        }

        parent?.subTasks?.remove(this)
    }

    @Ta5kBuilder
    protected fun Task<Unit>.success() {
        success(Unit)
    }

    @Ta5kBuilder
    protected fun failure(
        e: Throwable,
        stacktrace: MutableList<Task<*>> = mutableListOf(),
    ) {
        state = State.Failed
        unsubscribe()
        cancelSubTasks()
        stacktrace.add(this)

        parent?.onSubTaskFailure(this, e)
            ?: run {
                if (!verboseDebug) return@run
                val message =
                    buildString {
                        val first = stacktrace.firstOrNull() ?: return@buildString
                        append("${first.name} failed: ${e.message}\n")
                        stacktrace.drop(1).forEach {
                            append("  -> ${it.name}\n")
                        }
                    }
                logError(message)
            }
        parent?.onSubTaskCompletion(this)

        runSafe {
            failureCallbacks.forEach { it.invoke(this, e) }
            completionCallbacks.forEach { it.invoke(this) }
        }

        parent?.subTasks?.remove(this)
    }

    @Ta5kBuilder
    protected fun failure(message: String) = failure(IllegalStateException(message))

    @Ta5kBuilder
    fun activate() {
        if (state != State.Paused) return
        state = State.Running
    }

    @Ta5kBuilder
    fun pause() {
        if (state != State.Running) return
        state = State.Paused
    }

    @Ta5kBuilder
    fun cancel() = internalCancel(true)

    private fun internalCancel(removeFromParent: Boolean = true) {
        unsubscribe()
        runSafe { onCancel() }
        cancelSubTasks()
        if (removeFromParent) parent?.subTasks?.remove(this)
        if (parentPausing) parent?.activate()
        if (this is RootTask) return
        if (state == State.Completed || state == State.Cancelled) return
        state = State.Cancelled
    }

    @Ta5kBuilder
    fun cancelSubTasks() {
        subTasks.forEach { it.internalCancel(removeFromParent = false) }
        subTasks.clear()
    }

    @Ta5kBuilder
    protected open fun onSubTaskSuccess(subTask: Task<*>) {
        activate()
    }

    @Ta5kBuilder
    protected open fun onSubTaskCompletion(subTask: Task<*>) {
        activate()
    }

    @Ta5kBuilder
    protected open fun onSubTaskFailure(subTask: Task<*>, cause: Throwable) {
        failure(cause)
    }

    /**
     * Registers a callback for if the task succeeds.
     */
    @Ta5kBuilder
    fun onSuccess(callback: SafeContext.(Result) -> Unit): Task<Result> {
        successCallbacks.add(callback)
        return this
    }

    /**
     * Registers a callback for if the task fails.
     *
     * This could be mistaken for [onFail] which is used to wrap the given task with another task that runs a recovery task if this one fails.
     */
    @Ta5kBuilder
    fun onFailure(callback: SafeContext.(Throwable) -> Unit): Task<Result> {
        failureCallbacks.add(callback)
        return this
    }

    /**
     * Registers a callback for when the task completes.
     *
     * This is called regardless of whether the task succeeds or fails.
     */
    @Ta5kBuilder
    fun onCompletion(callback: SafeContext.() -> Unit): Task<Result> {
        completionCallbacks.add(callback)
        return this
    }

    override fun toString() =
        buildString { appendTaskTree(this@Task) }

    private fun StringBuilder.appendTaskTree(task: Task<*>, level: Int = 0, maxEntries: Int = 10) {
        if (task.state == State.Cancelled) return
        appendLine("${" ".repeat(level * 4)}${task.name}" + if (task !is RootTask) " [${task.state.display}] ${(task.age * 50).milliseconds}" else "")
        val left = task.subTasks.size - maxEntries
        if (left > 0) {
            appendLine("${" ".repeat((level + 1) * 4)}...and $left more tasks")
        }
        task.subTasks.takeLast(maxEntries).forEach {
            appendTaskTree(it, level + 1)
        }
    }
}
