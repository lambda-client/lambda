package com.lambda.task

import com.lambda.context.SafeContext
import com.lambda.event.Event
import com.lambda.event.EventFlow
import com.lambda.event.Subscriber
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.threading.runSafe
import com.lambda.util.BaritoneUtils
import com.lambda.util.Communication.info
import com.lambda.util.Communication.logError
import com.lambda.util.Communication.warn
import com.lambda.util.Nameable
import com.lambda.util.text.buildText
import com.lambda.util.text.color
import com.lambda.util.text.literal
import com.lambda.util.text.text
import kotlinx.coroutines.*
import net.minecraft.text.Text
import org.apache.commons.lang3.time.DurationFormatUtils
import java.awt.Color

/**
 * A [Task] represents a time-critical activity that executes a suspending action function.
 * It is designed to automate in-game activities without the need for strict event-based programming,
 * thanks to the use of suspending functions which allow for linear coding.
 *
 * [Result] is the type of the result that the task will return when it completes successfully.
 * In case the task should not return any result, [Unit] can be used as the type.
 *
 * A [Task] can have event listeners, but they are only active while the action function is running.
 *
 * It supports a builder pattern, allowing you to chain configuration methods like [withDelay],
 * [withTimeout], [withMaxAttempts], [withRepeats], [onSuccess], [onRetry], [onTimeout], [onFailure], and [onRepeat]
 * to construct a [Task] instance.
 * This makes it easy to build complex flows of nested tasks.
 *
 * CAUTION: When implementing the [onAction] function,
 * ensure that the function adheres to thread safety measures.
 * This includes avoiding write operations on non-synchronized in-game data
 * unless explicitly running on the game thread using `runSafeOnGameThread { ... }`.
 *
 * @property delay The delay before the task starts, in milliseconds.
 * @property timeout The maximum time that the task is allowed to run, in milliseconds.
 * @property tries The maximum number of attempts to execute the task before it is considered failed.
 * @property executions The number of times the task should be repeated.
 * @property onSuccess The action to be performed when the task completes successfully.
 * @property onRetry The action to be performed when the task is retried after a failure or timeout.
 * @property onTimeout The action to be performed when the task times out.
 * @property onRepeat The action to be performed each time the task is repeated.
 * @property onException The action to be performed when the task encounters an exception.
 */
abstract class Task<Result>(
    private var delay: Int = 0,
    private var timeout: Int = Int.MAX_VALUE,
    private var tries: Int = 1,
    private var repeats: Int = 1,
    private var onSuccess: SafeContext.(Task<Result>, Result) -> Unit = { _, _ -> },
    private var onRetry: SafeContext.(Task<Result>) -> Unit = {},
    private var onTimeout: SafeContext.(Task<Result>) -> Unit = {},
    private var onRepeat: SafeContext.(Task<Result>, Result, Int) -> Unit = { _, _, _ -> },
    private var onException: SafeContext.(Task<Result>, Throwable) -> Unit = { _, _ -> },
) : Nameable {
    private var parent: Task<*>? = null

    private var executions = 0
    private var attempted = 0
    private val subTasks = mutableListOf<Task<*>>()
    private var state = State.IDLE
    var age: Int = 0

    private val isDeactivated get() = state == State.DEACTIVATED
    val isActivated get() = state == State.ACTIVATED
    val isRunning get() = state == State.ACTIVATED || state == State.DEACTIVATED
    val isFailed get() = state == State.FAILED
    val isCompleted get() = state == State.COMPLETED
    override var name = this::class.simpleName ?: "Task"

    // ToDo: Better color management
    private val primaryColor = Color(0, 255, 0, 100)
    val info: Text
        get() = buildText {
            literal("Name ")
            color(primaryColor) { literal(name) }

            literal(" State ")
            color(primaryColor) { literal(state.name) }

            literal(" Runtime ")
            color(primaryColor) {
                literal(DurationFormatUtils.formatDuration(age * 50L, "HH:mm:ss,SSS"))
            }

            subTasks.forEach {
                literal("\n    ")
                text(it.info)
            }
        }

    val syncListeners = Subscriber()
    private val concurrentListeners = Subscriber()

    enum class State {
        IDLE,
        ACTIVATED,
        DEACTIVATED,
        CANCELLED,
        FAILED,
        COMPLETED
    }

    operator fun plus(other: Task<*>) = subTasks.add(other)

    init {
        listener<TickEvent.Pre> {
            parent?.let {
                it.age++
            }
            if (++age >= timeout) {
                onTimeout(this@Task)
                failure(TimeoutException(age, attempted))
            }
        }
    }

    @Ta5kBuilder
    open fun SafeContext.onStart() {}

    @Ta5kBuilder
    fun start(parent: Task<*>?, pauseParent: Boolean = true): Task<Result> {
        this.parent = parent
        executions++

        runSafe { onStart() }
        this.parent?.let { par ->
            par.subTasks.add(this)
            info("${par.name} started this task.")
            if (pauseParent && par.isActivated) {
                info("Pausing parent ${par.name}")
                par.deactivate()
            }
        } ?: info("Root started this task")

        activate()
        return this
    }

    @Ta5kBuilder
    private fun activate() {
        subTasks.firstOrNull { !it.isCompleted }?.let {
            info("Starting subtask ${it.name}")
            deactivate()
            it.start(this)
        } ?: run {
            info("Activated")
            state = State.ACTIVATED
            startListening()
        }
    }

    @Ta5kBuilder
    fun deactivate() {
        info("Deactivated")
        state = State.DEACTIVATED
        stopListening()
    }

    @Ta5kBuilder
    fun SafeContext.success(result: Result) {
        onSuccess(this@Task, result)

        if (executions < repeats) {
            executions++
            this@Task.info("Repeating task $executions/$repeats...")
            onRepeat(this@Task, result, executions)
            reset()
            return
        }

        this@Task.info("Task completed successfully after $attempted retries and $executions executions.")
        state = State.COMPLETED
        tidyUp()
    }

    @Ta5kBuilder
    fun cancel() {
        cancelSubTasks()
        state = State.CANCELLED
        tidyUp()
        runSafe { onCancel() }
    }

    @Ta5kBuilder
    fun cancelSubTasks() {
        subTasks
            .filter { it.isRunning }
            .forEach { it.cancel() }
    }

    @Ta5kBuilder
    fun failure(message: String) {
        failure(IllegalStateException(message))
    }

    @Ta5kBuilder
    fun failure(e: Throwable) {
        if (attempted < tries) {
            attempted++
            warn("Failed task with error: ${e.message}, retrying ($attempted/$tries) ...")
            runSafe {
                onRetry(this@Task)
            }
            reset()
            return
        }

        state = State.FAILED
        logError("Task failed after $attempted attempts with error: ${e.message}")
        tidyUp()
        runSafe {
            onException(this@Task, e)
        }
        parent?.failure(e)
    }

    /**
     * This function is called when the task is canceled.
     * It should be overridden for tasks that need to perform cleanup operations,
     * such as cancelling a block breaking progress, releasing resources,
     * or stopping any ongoing operations that were started by the task.
     */
    @Ta5kBuilder
    open fun SafeContext.onCancel() {}

    private fun tidyUp() {
        stopListening()
        BaritoneUtils.cancel()
        parent?.let {
//            it.subTasks.remove(this)
            if (it.isDeactivated) {
                it.activate()
            } else {
                info("Parent ${it.name} is activated, not reactivating")
            }
        }
    }

    @Ta5kBuilder
    private fun reset() {
        age = 0
    }

    private fun startListening() {
        EventFlow.syncListeners.subscribe(syncListeners)
        EventFlow.concurrentListeners.subscribe(concurrentListeners)
    }

    private fun stopListening() {
        EventFlow.syncListeners.unsubscribe(syncListeners)
        EventFlow.concurrentListeners.unsubscribe(concurrentListeners)
    }

    /**
     * Sets the delay before the task starts.
     *
     * @param delay The delay in milliseconds.
     * @return This task instance with the updated delay.
     */
    @Ta5kBuilder
    fun withDelay(delay: Int): Task<Result> {
        this.delay = delay
        return this
    }

    /**
     * Sets the timeout for a single attempt of the task
     *
     * @param timeout The timeout in milliseconds
     * @return This task instance with the updated timeout.
     */
    @Ta5kBuilder
    fun withTimeout(timeout: Int): Task<Result> {
        this.timeout = timeout
        return this
    }

    /**
     * Sets the maximum number of attempts to execute the task before it is considered failed.
     *
     * @param maxAttempts The maximum number of attempts.
     * @return This task instance with the updated maximum attempts.
     */
    @Ta5kBuilder
    fun withMaxAttempts(maxAttempts: Int): Task<Result> {
        this.tries = maxAttempts
        return this
    }

    /**
     * Sets the number of times the task should be repeated.
     *
     * @param repeats The number of repeats.
     * @return This task instance with the updated number of repeats.
     */
    @Ta5kBuilder
    fun withRepeats(repeats: Int): Task<Result> {
        this.repeats = repeats
        return this
    }

    /**
     * Sets the action to be performed when the task completes successfully.
     *
     * @param action The action to be performed.
     * @return The task instance with the updated success action.
     */
    @Ta5kBuilder
    fun onSuccess(action: SafeContext.(Task<Result>, Result) -> Unit): Task<Result> {
        this.onSuccess = action
        return this
    }

    /**
     * Sets the action to be performed when the task is retried after a failure or timeout.
     *
     * @param action The action to be performed.
     * @return The task instance with the updated retry action.
     */
    @Ta5kBuilder
    fun onRetry(action: SafeContext.(Task<Result>) -> Unit): Task<Result> {
        this.onRetry = action
        return this
    }

    /**
     * Sets the action to be performed when the task times out.
     *
     * @param action The action to be performed.
     * @return The task instance with the updated timeout action.
     */
    @Ta5kBuilder
    fun onTimeout(action: SafeContext.(Task<Result>) -> Unit): Task<Result> {
        this.onTimeout = action
        return this
    }

    /**
     * Sets the action to be performed when the task encounters an exception.
     *
     * @param action The action to be performed.
     * @return The task instance with the updated exception action.
     */
    @Ta5kBuilder
    fun onFailure(action: SafeContext.(Task<Result>, Throwable) -> Unit): Task<Result> {
        this.onException = action
        return this
    }

    /**
     * Sets the action to be performed each time the task is repeated.
     *
     * @param action The action to be performed.
     * @return The task instance with the updated repeat action.
     */
    @Ta5kBuilder
    fun onRepeat(action: SafeContext.(Task<Result>, Result, Int) -> Unit): Task<Result> {
        this.onRepeat = action
        return this
    }

    @TaskCha1nBuilder
    fun withSubTasks(subTaskBuilder: SubTaskBuilder.(Task<*>) -> Unit): Task<Result> {
        with(SubTaskBuilder()) {
            subTaskBuilder(this@Task)
            subTasks.addAll(tasks)
        }
        return this
    }

    @TaskCha1nBuilder
    inline fun <reified T : Event> withListener(
        event: TickEvent.Pre, crossinline action: SafeContext.(Task<Result>) -> Unit
    ): Task<Result> {
        listener<T> {
            action(this@Task)
        }
        return this
    }

    @TaskCha1nBuilder
    fun withName(name: String): Task<Result> {
        this.name = name
        return this
    }

    class TimeoutException(age: Int, attempts: Int) : Exception("Task timed out after $age ticks and $attempts attempts")

    class SubTaskBuilder {
        val tasks = mutableListOf<Task<*>>()
    }

    companion object {
        @TaskCha1nBuilder
        fun emptyTask() = object : Task<Unit>() {
            override fun SafeContext.onStart() {}
        }

        @TaskCha1nBuilder
        fun buildTask(
            block: SafeContext.() -> Unit
        ) = object : Task<Unit>() {
            override fun SafeContext.onStart() {
                block()
            }
        }

        @TaskCha1nBuilder
        inline fun <reified R> buildTaskWithReturn(
            crossinline block: SafeContext.() -> Unit
        ) = object : Task<R>() {
            override fun SafeContext.onStart() {
                block()
            }
        }
    }
}