package com.lambda.task

import com.lambda.Lambda.LOG
import com.lambda.context.SafeContext
import com.lambda.event.EventFlow
import com.lambda.event.Subscriber
import com.lambda.threading.runSafe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

abstract class Task<Result>(
    private var delay: Long = 0L,
    private var timeout: Long = Long.MAX_VALUE,
    private var maxAttempts: Int = 1,
    private var repeats: Int = 1,
    private var onSuccess: (suspend (Result) -> Unit) = {},
    private var onRetry: (suspend Task<Result>.() -> Unit) = {},
    private var onTimeout: (suspend Task<Result>.() -> Unit) = {},
    private var onRepeat: (suspend Task<Result>.(Int) -> Unit) = {},
    private var onException: (suspend Task<Result>.(Throwable) -> Unit) = {},
) {
    val syncListeners = Subscriber()
    private val concurrentListeners = Subscriber()

    /**
     * Make sure to only do read operations on the game thread!
     * Use `runSafeOnGameThread { ... }` to execute safe write operations on the game thread.
     * @return The [Result] of the action
     */
    abstract suspend fun SafeContext.onAction(): Result

    open fun SafeContext.onCancel() {}

    suspend fun execute(): TaskResult<Result> {
        var attempt = 1
        var iteration = 0

        do {
            if (delay > 0) {
                LOG.info("Delaying task $name for $delay ms")
                delay(delay)
            }

            startListening()

            try {
                withTimeout(timeout) {
                    runSafe {
                        onAction()
                    }
                }?.let { result ->
                    iteration++

                    if (iteration == repeats) {
                        onSuccess(result)
                        stopListening()
                        return TaskResult.Success(result)
                    }

                    onRepeat(iteration)
                }
            } catch (e: CancellationException) {
                stopListening()
                LOG.warn("Coroutine for task $name cancelled")
                runSafe {
                    onCancel()
                }
                return TaskResult.Cancelled
            } catch (e: TimeoutCancellationException) {
                attempt++
                LOG.warn("Task $name timed out after $age ms and $attempt attempts")
                onRetry()
                stopListening()
            } catch (e: Throwable) {
                attempt++
                LOG.error("Task $name failed after $age ms and $attempt attempts", e)
                onException(e)
                stopListening()
                return TaskResult.Failure(e)
            }

            stopListening()
        } while (attempt < maxAttempts || iteration <= repeats)

        LOG.error("Task $name fully timed out after $age ms and $attempt attempts")
        onTimeout()
        return TaskResult.Timeout(timeout, attempt)
    }

    private fun stopListening() {
        EventFlow.syncListeners.unsubscribe(syncListeners)
        EventFlow.concurrentListeners.unsubscribe(concurrentListeners)

        // ToDo: cancel baritone
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

    private val creationTimestamp = System.currentTimeMillis()
    val age: Long get() = System.currentTimeMillis() - creationTimestamp
    val name: String get() = this::class.simpleName ?: "Task"

    fun withDelay(delay: Long): Task<Result> {
        this.delay = delay
        return this
    }

    fun withTimeout(timeout: Long): Task<Result> {
        this.timeout = timeout
        return this
    }

    fun withMaxAttempts(maxAttempts: Int): Task<Result> {
        this.maxAttempts = maxAttempts
        return this
    }

    fun withRepeats(repeats: Int): Task<Result> {
        this.repeats = repeats
        return this
    }

    fun onSuccess(action: suspend (Result) -> Unit): Task<Result> {
        this.onSuccess = action
        return this
    }

    fun onRetry(action: suspend Task<Result>.() -> Unit): Task<Result> {
        this.onRetry = action
        return this
    }

    fun onTimeout(action: suspend Task<Result>.() -> Unit): Task<Result> {
        this.onTimeout = action
        return this
    }

    fun onFailure(action: suspend Task<Result>.(Throwable) -> Unit): Task<Result> {
        this.onException = action
        return this
    }

    fun onRepeat(action: suspend Task<Result>.(Int) -> Unit): Task<Result> {
        this.onRepeat = action
        return this
    }
}