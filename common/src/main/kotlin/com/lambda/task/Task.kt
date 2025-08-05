/*
 * Copyright 2025 Lambda
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
import com.lambda.threading.runSafe
import com.lambda.util.Communication.logError
import com.lambda.util.Nameable
import com.lambda.util.StringUtils.capitalize
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration

typealias TaskGenerator<R> = SafeContext.(R) -> Task<*>
typealias TaskGeneratorOrNull<R> = SafeContext.(R) -> Task<*>?
typealias TaskGeneratorUnit<R> = SafeContext.(R) -> Unit

abstract class Task<Result> : Nameable, Muteable {
    var parent: Task<*>? = null
    val subTasks = mutableListOf<Task<*>>()
    var state = State.INIT
    override val isMuted: Boolean get() = state == State.PAUSED || state == State.INIT
    var age = 0
    private val depth: Int get() = parent?.depth?.plus(1) ?: 0
    val isCompleted get() = state == State.COMPLETED
    val size: Int get() = subTasks.sumOf { it.size } + 1

    private var nextTask: TaskGenerator<Result>? = null
    private var nextTaskOrNull: TaskGeneratorOrNull<Result>? = null
    private var onFinish: TaskGeneratorUnit<Result>? = null

    enum class State {
        INIT,
        RUNNING,
        PAUSED,
        CANCELLED,
        FAILED,
        COMPLETED;

        val display get() = name.lowercase().capitalize()
    }

    init {
        listen<TickEvent.Pre> { age++ }
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
    open fun SafeContext.onStart() {}

    /**
     * This function is called when the task is canceled.
     * It can be overridden for tasks that need to perform cleanup operations,
     * such as cancelling a block breaking progress, releasing resources,
     * or stopping any ongoing operations that were started by the task.
     */
    @Ta5kBuilder
    open fun SafeContext.onCancel() {}

    /**
     * Executes the current task as a subtask of the specified owner task.
     *
     * This method adds the current task to the owner's subtasks, sets the parent relationship,
     * logs the execution details, and invokes the necessary lifecycle hooks. Additionally,
     * it manages the state of the parent task and starts any required listeners for execution.
     *
     * @param owner The parent task that will execute this task as a child. Must not be the same as this task.
     * @param pauseParent Defines whether the parent task should be paused during the execution of this task. Defaults to `true`.
     * @return The current task instance as a `Task<Result>` to support chaining or further configuration.
     * @throws IllegalArgumentException if the owner task is the same as the task being executed.
     */
    @Ta5kBuilder
    fun execute(owner: Task<*>, pauseParent: Boolean = true): Task<Result> {
        require(owner != this) { "Cannot execute a task as a child of itself" }
        owner.subTasks.add(this)
        parent = owner
        LOG.info("${owner.name} started $name")
        if (pauseParent) {
            LOG.info("$name pausing parent ${owner.name}")
            if (owner !is RootTask) owner.pause()
        }
        state = State.RUNNING
        runSafe { runCatching { onStart() }.onFailure { failure(it) } }
        return this
    }

    @Ta5kBuilder
    fun success(result: Result) {
        unsubscribe()
        state = State.COMPLETED
        runSafe {
            executeNextTask(result)
        }
    }

    @Ta5kBuilder
    fun Task<Unit>.success() {
        success(Unit)
    }

    @Ta5kBuilder
    fun activate() {
        if (state != State.PAUSED) return
        state = State.RUNNING
    }

    @Ta5kBuilder
    fun pause() {
        if (state != State.RUNNING) return
        state = State.PAUSED
    }

    private fun SafeContext.executeNextTask(result: Result) {
        nextTask?.let { taskGen ->
            val task = taskGen(this, result)
            nextTask = null
            parent?.let { owner -> task.execute(owner) }
        } ?: nextTaskOrNull?.let { taskGen ->
            val task = taskGen(this, result)
            nextTaskOrNull = null
            parent?.let { owner -> task?.execute(owner) }
        } ?: run {
            onFinish?.invoke(this, result)
            parent?.activate()
            onFinish = null
        }
    }

    @Ta5kBuilder
    fun cancel() {
        runSafe { onCancel() }
        cancelSubTasks()
        if (this is RootTask) return
        if (state == State.COMPLETED || state == State.CANCELLED) return
        state = State.CANCELLED
        unsubscribe()
    }

    @Ta5kBuilder
    fun cancelSubTasks() {
        subTasks.forEach { it.cancel() }
    }

    fun clear() {
        subTasks.forEach { it.clear() }
        subTasks.clear()
    }

    @Ta5kBuilder
    fun failure(message: String) = failure(IllegalStateException(message))

    @Ta5kBuilder
    fun failure(
        e: Throwable,
        stacktrace: MutableList<Task<*>> = mutableListOf(),
    ) {
        state = State.FAILED
        unsubscribe()
        stacktrace.add(this)
        parent?.failure(e, stacktrace) ?: run {
            val message = buildString {
                stacktrace.firstOrNull()?.let { first ->
                    append("${first.name} failed: ${e.message}\n")
                    stacktrace.drop(1).forEach {
                        append("  -> ${it.name}\n")
                    }
                }
            }
            LOG.error(message, e)
            logError(message)
        }
    }

    /**
     * Specifies the next task to execute after the current task completes successfully.
     *
     * This method links the current task to the specified `task`, creating a sequential
     * execution flow where the `task` will be executed immediately after the current task.
     *
     * @param task The task that should be executed following the successful completion
     *             of the current task.
     * @return The current task instance (`Task<R>`) to allow method chaining.
     */
    @Ta5kBuilder
    infix fun then(task: Task<*>): Task<Result> {
        require(task != this) { "Cannot link a task to itself" }
        nextTask = { task }
        return this
    }

    /**
     * Chains multiple tasks to be executed sequentially after the current task.
     *
     * This method establishes an ordered execution flow between tasks, where each task
     * in the provided list will trigger the execution of the next task upon completion.
     * It effectively links the current task to the first task in the given array
     * and ensures that the sequence is executed in order.
     *
     * @param task A vararg list of tasks to be linked sequentially after the current task.
     *             These tasks are executed in the order they are provided.
     * @return The current task instance (`Task<R>`) to allow method chaining.
     */
    @Ta5kBuilder
    fun then(vararg task: Task<*>): Task<Result> {
        (listOf(this) + task).zipWithNext { current, next ->
            current then next
        }
        return this
    }

    /**
     * Adds a subsequent task to the current task's execution sequence.
     *
     * This method specifies the next task to be executed after the current task
     * completes successfully. The task is generated dynamically using the provided
     * `TaskGenerator`. This allows chaining tasks together in a flexible manner.
     *
     * @param taskGenerator A function that generates the next task based on the result
     *                      of the current task. It takes a `SafeContext` and the result
     *                      of type `R` as input and returns a new `Task`.
     * @return The current task instance (`Task<R>`) to allow method chaining.
     */
    @Ta5kBuilder
    fun then(taskGenerator: TaskGenerator<Result>): Task<Result> {
        require(nextTask == null) { "Cannot link multiple tasks to a single task" }
        nextTask = taskGenerator
        return this
    }

    /**
     * Adds a subsequent task to the current task's execution sequence conditionally.
     *
     * This method specifies the next task to be executed after the current task
     * completes successfully. The next task is generated dynamically using the provided
     * [TaskGeneratorOrNull]. This allows chaining tasks together flexibly,
     * where the next task is conditionally determined or null.
     *
     * @param taskGenerator A function that generates the next task based on the
     *                      result of the current task. It takes a `SafeContext`
     *                      and the result of type `R` as input and returns a new
     *                      `Task` or null if no next task is required.
     * @return The current task instance (`Task<R>`) to allow method chaining.
     */
    @Ta5kBuilder
    fun thenOrNull(taskGenerator: TaskGeneratorOrNull<Result>): Task<Result> {
        require(nextTask == null) { "Cannot link multiple tasks to a single task" }
        nextTaskOrNull = taskGenerator
        return this
    }

    /**
     * Registers a finalization action to be executed after the current task completes.
     *
     * This method allows specifying a finalization function that runs upon completion
     * of the task, regardless of its outcome (success or failure). It is typically used
     * for cleanup operations or logging after a task finishes execution.
     *
     * @param onFinish The finalization action to be executed. This function receives
     *                 the task's result of type `R` within a `SafeContext`.
     * @return The current task instance (`Task<R>`) to allow method chaining.
     */
    @Ta5kBuilder
    fun finally(onFinish: TaskGeneratorUnit<Result>): Task<Result> {
        require(this.onFinish == null) { "Cannot link multiple finally blocks to a single task" }
        this.onFinish = onFinish
        return this
    }

    val duration: String get() =
        (age * 50).toDuration(DurationUnit.MILLISECONDS).toComponents { days, hours, minutes, seconds, nanoseconds ->
            "${"%03d".format(days)}:${"%02d".format(hours)}:${"%02d".format(minutes)}:${"%02d".format(seconds)}.${"${nanoseconds / 1_000_000}".take(2)}"
        }

    override fun toString() =
        buildString { appendTaskTree(this@Task) }

    private fun StringBuilder.appendTaskTree(task: Task<*>, level: Int = 0, maxEntries: Int = 10) {
        if (task.state == State.CANCELLED) return
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
