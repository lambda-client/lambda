package com.lambda.task

import baritone.api.BaritoneAPI
import com.lambda.Lambda.LOG
import com.lambda.context.SafeContext
import com.lambda.event.Event
import com.lambda.event.EventFlow
import com.lambda.event.Subscriber
import com.lambda.threading.runSafe
import com.lambda.util.Nameable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

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
 * @property maxAttempts The maximum number of attempts to execute the task before it is considered failed.
 * @property repeats The number of times the task should be repeated.
 * @property onSuccess The action to be performed when the task completes successfully.
 * @property onRetry The action to be performed when the task is retried after a failure or timeout.
 * @property onTimeout The action to be performed when the task times out.
 * @property onRepeat The action to be performed each time the task is repeated.
 * @property onException The action to be performed when the task encounters an exception.
 */
abstract class Task<Result>(
    private var delay: Long = 0L,
    private var timeout: Long = Long.MAX_VALUE,
    private var maxAttempts: Int = 1,
    open var repeats: Int = 1,
    private var onSuccess: (suspend (Result) -> Unit) = {},
    private var onRetry: (suspend Task<Result>.() -> Unit) = {},
    private var onTimeout: (suspend Task<Result>.() -> Unit) = {},
    private var onRepeat: (suspend Task<Result>.(Int) -> Unit) = {},
    private var onException: (suspend Task<Result>.(Throwable) -> Unit) = {},
) : Nameable {
    private val creationTimestamp = System.currentTimeMillis()
    private val age: Long get() = System.currentTimeMillis() - creationTimestamp
    override val name: String get() = this::class.simpleName ?: "Task"

    val syncListeners = Subscriber()
    private val concurrentListeners = Subscriber()

    operator fun plus(other: Task<*>) = TaskChain(listOf(this, other))

    /**
     * Executes the main action of the task.
     *
     * This function should only perform read operations on the game thread due to potential concurrency issues.
     * If write operations are necessary, they should be wrapped in the `runSafeOnGameThread { ... }` function to ensure thread safety.
     *
     * @return The result of the action, of type [Result].
     */
    abstract suspend fun onAction(): Result

    /**
     * This function is called when the task is canceled.
     * It should be overridden for tasks that need to perform cleanup operations,
     * such as cancelling a block breaking progress, releasing resources,
     * or stopping any ongoing operations that were started by the task.
     */
    open fun SafeContext.onCancel() {}

    /**
     * Executes the task with the configured parameters.
     *
     * This function will attempt to execute the task for a specified number of attempts and repeats.
     * It handles delays before task execution, task timeouts, and task cancellations.
     * It also manages the lifecycle of event listeners associated with the task.
     *
     * @return The result of the task execution, represented as a [TaskResult].
     * This could be a successful result ([TaskResult.Success]), a cancellation ([TaskResult.Cancelled]),
     * a failure ([TaskResult.Failure]) due to an exception, or a timeout
     * ([TaskResult.Timeout]).
     */
    suspend fun execute(): TaskResult<Result> {
        var attempt = 1
        var iteration = 0

        do {
            if (delay > 0) {
                LOG.info("Delaying task $name for $delay ms")
                delay(delay)
            }

            startListening()
            iteration++

            try {
                withTimeout(timeout) {
                    onAction()
                }?.let { result ->
                    if (iteration == repeats) {
                        onSuccess(result)
                        tidyUp()
                        return TaskResult.Success(result)
                    }

                    onRepeat(iteration)
                }
            } catch (e: TimeoutCancellationException) {
                attempt++
                LOG.warn("Task $name timed out after $age ms and $attempt attempts")
                onRetry()
                tidyUp()
            } catch (e: CancellationException) {
                tidyUp()
                LOG.warn("Coroutine for task $name cancelled")
                return TaskResult.Cancelled
            } catch (e: Throwable) {
                attempt++
                LOG.error("Task $name failed after $age ms and $attempt attempts", e)
                onException(e)
                tidyUp()
                return TaskResult.Failure(e)
            }

            tidyUp()
        } while (attempt < maxAttempts || iteration <= repeats)

        LOG.error("Task $name fully timed out after $age ms and $attempt attempts")
        onTimeout()
        return TaskResult.Timeout(timeout, attempt)
    }

    private fun tidyUp() {
        runSafe {
            onCancel()
        }

        EventFlow.syncListeners.unsubscribe(syncListeners)
        EventFlow.concurrentListeners.unsubscribe(concurrentListeners)

        BaritoneAPI.getProvider().primaryBaritone?.pathingBehavior?.cancelEverything()
    }

    private fun startListening() {
        EventFlow.syncListeners.subscribe(syncListeners)
        EventFlow.concurrentListeners.subscribe(concurrentListeners)
    }

    fun cancel() {
        runSafe {
            onCancel()
        }
    }

    /**
     * Sets the delay before the task starts.
     *
     * @param delay The delay in milliseconds.
     * @return This task instance with the updated delay.
     */
    @Ta5kBuilder
    fun withDelay(delay: Long): Task<Result> {
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
    fun withTimeout(timeout: Long): Task<Result> {
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
        this.maxAttempts = maxAttempts
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
    fun onSuccess(action: suspend (Result) -> Unit): Task<Result> {
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
    fun onRetry(action: suspend Task<Result>.() -> Unit): Task<Result> {
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
    fun onTimeout(action: suspend Task<Result>.() -> Unit): Task<Result> {
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
    fun onFailure(action: suspend Task<Result>.(Throwable) -> Unit): Task<Result> {
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
    fun onRepeat(action: suspend Task<Result>.(Int) -> Unit): Task<Result> {
        this.onRepeat = action
        return this
    }

    companion object {
        fun <T> (suspend () -> T).toTask(name: String = "Task") = object : Task<T>() {
            override val name = name
            override suspend fun onAction() = this@toTask()
        }
    }
}