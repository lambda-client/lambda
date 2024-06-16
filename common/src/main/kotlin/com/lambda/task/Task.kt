package com.lambda.task

import com.lambda.Lambda.LOG
import com.lambda.context.SafeContext
import com.lambda.event.Event
import com.lambda.event.EventFlow
import com.lambda.event.Subscriber
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.module.modules.client.TaskFlow
import com.lambda.threading.runConcurrent
import com.lambda.threading.runGameScheduled
import com.lambda.threading.runSafe
import com.lambda.util.Communication.logError
import com.lambda.util.Communication.warn
import com.lambda.util.Nameable
import com.lambda.util.text.buildText
import com.lambda.util.text.color
import com.lambda.util.text.literal
import com.lambda.util.text.text
import kotlinx.coroutines.delay
import net.minecraft.text.Text
import org.apache.commons.lang3.time.DurationFormatUtils
import java.awt.Color

/**
 * A [Task] represents a time-critical activity.
 * It automates in-game activities through event-based programming, leveraging game events to control task flow.
 *
 * [Result] is the type of the result that the task will return when it completes successfully.
 * In case the task should not return any result, [Unit] can be used as the type.
 *
 * A [Task] can have event listeners that are active while the task is running.
 * The task will attempt to execute its subtasks sequentially, and can either keep listening
 * or stop receiving events while the subtasks are running.
 *
 * It supports a builder pattern, allowing you to chain configuration methods like [withDelay],
 * [withTimeout], [withMaxAttempts], [withRepeats], [onSuccess], [onRetry], [onTimeout], [onFailure], and [onRepeat]
 * to construct a [Task] instance.
 * This makes it easy to build complex flows of nested tasks.
 *
 * @property delay The delay before the task starts, in milliseconds.
 * @property timeout The maximum time that the task is allowed to run, in ticks.
 * @property tries The maximum number of attempts to execute the task before it is considered failed.
 * @property executions The number of times the task should be repeated.
 * @property onSuccess The action to be performed when the task completes successfully.
 * @property onRetry The action to be performed when the task is retried after a failure or timeout.
 * @property onTimeout The action to be performed when the task times out.
 * @property onRepeat The action to be performed each time the task is repeated.
 * @property onException The action to be performed when the task encounters an exception.
 */
abstract class Task<Result> : Nameable {
    open var delay: Int = 0
    open var timeout: Int = Int.MAX_VALUE
    open var tries: Int = 0
    open var repeats: Int = 0
    open var cooldown: Int = TaskFlow.taskCooldown
    open var onStart: SafeContext.(Task<Result>) -> Unit = {}
    open var onSuccess: SafeContext.(Task<Result>, Result) -> Unit = { _, _ -> }
    open var onRetry: SafeContext.(Task<Result>) -> Unit = {}
    open var onTimeout: SafeContext.(Task<Result>) -> Unit = {}
    open var onRepeat: SafeContext.(Task<Result>, Result, Int) -> Unit = { _, _, _ -> }
    open var onException: SafeContext.(Task<Result>, Throwable) -> Unit = { _, _ -> }

    open var pausable = true

    var parent: Task<*>? = null
    private val root: Task<*> get() = parent?.root ?: this
    private val depth: Int get() = parent?.depth?.plus(1) ?: 0

    private var executions = 0
    private var attempted = 0
    private val subTasks = mutableListOf<Task<*>>()
    private var state = State.IDLE
    var age = 0

    private val isDeactivated get() = state == State.DEACTIVATED
    val isActivated get() = state == State.ACTIVATED
    val isRunning get() = state == State.ACTIVATED || state == State.DEACTIVATED
    val isFailed get() = state == State.FAILED
    val isCompleted get() = state == State.COMPLETED || state == State.COOLDOWN
    val isRoot get() = parent == null
    override var name = this::class.simpleName ?: "Task"
    val identifier get() = "$name@${hashCode()}"

    // ToDo: Better color management
    private val primaryColor = Color(0, 255, 0, 100)

    val syncListeners = Subscriber()
    private val concurrentListeners = Subscriber()

    enum class State {
        IDLE,
        ACTIVATED,
        DEACTIVATED,
        CANCELLED,
        FAILED,
        COOLDOWN,
        COMPLETED,
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

    /**
     * "Typo" in name is used to force the dsl style green
     * (color is based on name hash, don't ask me who came up with this)
     */
    @DslMarker
    annotation class Ta5kBuilder

    @Ta5kBuilder
    open fun SafeContext.onStart() {}

    @Ta5kBuilder
    fun start(parent: Task<*>?, pauseParent: Boolean = true): Task<Result> {
        executions++
        val owner = parent ?: RootTask
        owner.subTasks.add(this)

        LOG.info("${owner.identifier} started $identifier")
        this.parent = owner
        if (pauseParent && owner.isActivated && !owner.isRoot) {
            LOG.info("$identifier deactivating parent ${owner.identifier}")
            owner.deactivate()
        }

        activate()
        runSafe {
            onStart(this@Task)
            onStart()
        }
        return this
    }

    @Ta5kBuilder
    fun activate() {
        if (isActivated) return
        state = State.ACTIVATED
        startListening()
    }

    @Ta5kBuilder
    fun deactivate() {
        if (isDeactivated) return
        state = State.DEACTIVATED
        stopListening()
    }

    @Ta5kBuilder
    fun SafeContext.success(result: Result) {
        if (executions < repeats) {
            executions++
            LOG.info("Repeating $identifier $executions/$repeats...")
            onRepeat(this@Task, result, executions)
            reset()
            return
        }

        stopListening()
        if (cooldown > 0) {
            state = State.COOLDOWN
            runConcurrent {
                delay(cooldown.toLong())
                runGameScheduled {
                    if (state == State.COOLDOWN) finish(result)
                }
            }
        } else finish(result)
    }

    private fun SafeContext.finish(result: Result) {
        state = State.COMPLETED
        try {
            onSuccess(this@Task, result)
        } catch (e: ClassCastException) {
            result?.let {
                LOG.error("Failed to cast result of $identifier to ${it::class.simpleName}")
            }
            failure(e)
        }

        notifyParent()
        LOG.info("$identifier completed successfully after $attempted retries and $executions executions.")
    }

    @Ta5kBuilder
    fun cancel() {
        if (state == State.CANCELLED) return

        cancelSubTasks()
        state = State.CANCELLED
        stopListening()
        runSafe { onCancel() }
        LOG.info("$identifier was cancelled")
    }

    @Ta5kBuilder
    fun cancelSubTasks() {
        subTasks.forEach { it.cancel() }
    }

    @Ta5kBuilder
    fun failure(message: String) {
        failure(IllegalStateException(message))
    }

    @Ta5kBuilder
    fun failure(
        e: Throwable,
        stacktrace: MutableList<Task<*>> = mutableListOf()
    ) {
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
        stopListening()
        runSafe { onException(this@Task, e) }
        stacktrace.add(this)
        parent?.failure(e, stacktrace) ?: run {
            val message = buildString {
                stacktrace.firstOrNull()?.let { first ->
                    append("${first.identifier} failed: ${e.message}\n")
                    stacktrace.drop(1).forEach {
                        append("  -> ${it.identifier}\n")
                    }
                }
            }
            LOG.error(message, e)
            logError(message)
        }
    }

    /**
     * This function is called when the task is canceled.
     * It should be overridden for tasks that need to perform cleanup operations,
     * such as cancelling a block breaking progress, releasing resources,
     * or stopping any ongoing operations that were started by the task.
     */
    @Ta5kBuilder
    open fun SafeContext.onCancel() {}

    private fun notifyParent() {
        parent?.let { par ->
            when {
                par.isCompleted -> {
                    LOG.info("$identifier completed parent ${par.identifier}")
                    par.notifyParent()
                }
                !par.isActivated -> {
                    LOG.info("$identifier reactivated parent ${par.identifier}")
                    par.activate()
                }
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
     * @param delay The delay in ticks.
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
     * @param timeout The timeout in ticks.
     * @return This task instance with the updated timeout.
     */
    @Ta5kBuilder
    fun withTimeout(timeout: Int): Task<Result> {
        this.timeout = timeout
        return this
    }

    /**
     * Sets the cooldown period for the task.
     *
     * The cooldown period is the time that the task will wait before it the next task can be executed.
     *
     * @param cooldown The cooldown period in milliseconds.
     * @return This task instance with the updated cooldown period.
     */
    @Ta5kBuilder
    fun withCooldown(cooldown: Int): Task<Result> {
        this.cooldown = cooldown
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
     * Sets the action to be performed when the task starts.
     *
     * @param action The action to be performed.
     * @return The task instance with the updated start action.
     */
    @Ta5kBuilder
    fun onStart(action: SafeContext.(Task<Result>) -> Unit): Task<Result> {
        this.onStart = action
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
     * This method allows you to chain another task that will be started upon the successful completion of the current task.
     * The action provided as a parameter will be used to create the next task.
     * The context of the action, the current task, and the result of the current task are passed as parameters to the action.
     *
     * @param action A lambda function that takes the current task and its result as parameters and returns the next task to be started.
     * @return The current task instance with the updated success action.
     */
    @Ta5kBuilder
    fun thenRun(action: SafeContext.(Task<Result>, Result) -> Task<*>): Task<Result> {
        this.onSuccess = { task, result ->
            action(this, task, result).start(task)
        }
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
    
    @Ta5kBuilder
    inline fun <reified T : Event> withListener(
        crossinline action: SafeContext.(Task<Result>) -> Unit
    ): Task<Result> {
        listener<T> {
            action(this@Task)
        }
        return this
    }

    @Ta5kBuilder
    fun withName(name: String): Task<Result> {
        this.name = name
        return this
    }

    class TimeoutException(age: Int, attempts: Int) : Exception("Task timed out after $age ticks and $attempts attempts")

    companion object {
        val MAX_DEPTH = 20
        const val MAX_DEBUG_ENTRIES = 15

        @Ta5kBuilder
        fun emptyTask(
            name: String = "EmptyTask",
        ): Task<Unit> = object : Task<Unit>() {
            init { this.name = name }
            override fun SafeContext.onStart() { success(Unit) }
        }
        
        @Ta5kBuilder
        fun failTask(
            message: String
        ): Task<Unit> = object : Task<Unit>() {
            init { this.name = "FailTask" }
            override fun SafeContext.onStart() {
                failure(message)
            }
        }

        @Ta5kBuilder
        fun failTask(
            e: Throwable
        ): Task<Unit> = object : Task<Unit>() {
            init { this.name = "FailTask" }
            override fun SafeContext.onStart() {
                failure(e)
            }
        }

        @Ta5kBuilder
        fun buildTask(
            name: String = "Task",
            block: SafeContext.() -> Unit
        ) = object : Task<Unit>() {
            init { this.name = name }
            override fun SafeContext.onStart() {
                try {
                    success(block())
                } catch (e: Throwable) {
                    failure(e)
                }
            }
        }

        @Ta5kBuilder
        inline fun <reified R> buildTaskWithReturn(
            crossinline block: SafeContext.() -> R
        ) = object : Task<R>() {
            override fun SafeContext.onStart() {
                try {
                    success(block())
                } catch (e: Throwable) {
                    failure(e)
                }
            }
        }
    }

    val info: Text
        get() = buildText {
            literal("Name ")
            color(primaryColor) { literal(name) }

            literal(" State ")
            color(primaryColor) { literal(state.name) }

            literal(" Runtime ")
            color(primaryColor) {
                literal(DurationFormatUtils.formatDuration(age * 50L, "HH:mm:ss,SSS").dropLast(1))
            }

            val display = subTasks.reversed().take(MAX_DEBUG_ENTRIES)
            display.forEach {
                literal("\n${"  ".repeat(depth + 1)}")
                text(it.info)
            }

            val left = subTasks.size - display.size
            if (left > 0) {
                literal("\n${"  ".repeat(depth + 1)}And ")
                color(primaryColor) {
                    literal("$left")
                }
                literal(" more...")
            }

        }
}