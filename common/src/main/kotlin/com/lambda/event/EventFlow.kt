package com.lambda.event

import com.lambda.context.SafeContext
import com.lambda.event.callback.ICancellable
import com.lambda.event.listener.Listener
import com.lambda.threading.runConcurrent
import com.lambda.threading.runSafe
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*


/**
 * [EventFlow] is an object that manages the flow ([MutableSharedFlow]) of [Event]s in the application.
 * It provides methods to post [Event]s to both synchronous and asynchronous [Listener]s.
 *
 * The [EventFlow] also provides methods to unsubscribe from event flows for a specific event type.
 */
object EventFlow {
    /**
     * [lambdaScope] is a [CoroutineScope] which is used to launch coroutines.
     *
     * It is defined with [Dispatchers.Default] which is optimized for CPU-intensive work
     * such as sorting large lists, doing complex calculations and similar.
     *
     * The [SupervisorJob] is used so that failure or cancellation of one child does not
     * lead to the failure or cancellation of the parent or its other children, which is
     * useful when you have multiple independent [Job]s running in parallel.
     */
    val lambdaScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    val concurrentFlow = MutableSharedFlow<Event>(
        extraBufferCapacity = 1000,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val syncListeners = Subscriber()
    val concurrentListeners = Subscriber()

    init {
        // parallel event execution on dedicated threads
        runConcurrent {
            concurrentFlow
                // early filter to avoid an unnecessary collection
                .filter { concurrentListeners.containsKey(it::class) }
                .filterNot { it is ICancellable && it.isCanceled() }
                .collect { event ->
                    event.executeListenerConcurrently()
                }
        }
    }

    suspend inline fun <reified E : Event> awaitEvent(
        noinline predicate: SafeContext.(E) -> Boolean = { true },
    ) = concurrentFlow.filterIsInstance<E>().first {
        runSafe {
            predicate(it)
        } ?: false
    }

    suspend inline fun <reified E : Event> awaitEventUnsafe(
        noinline predicate: (E) -> Boolean = { true },
    ) = concurrentFlow.filterIsInstance<E>().first(predicate)

    suspend inline fun <reified E : Event> awaitEvent(
        timeout: Long,
        noinline predicate: (E) -> Boolean = { true },
    ) = runBlocking {
            withTimeout(timeout) {
                concurrentFlow.filterIsInstance<E>().first(predicate)
            }
        }

    suspend inline fun <reified E : Event> awaitEvents(
        crossinline predicate: (E) -> Boolean = { true },
    ): Flow<E> = flow {
        concurrentFlow
            .filterIsInstance<E>()
            .filter { predicate(it) }
            .collect {
                emit(it)
            }
    }

    /**
     * Posts an [Event] to the event flow [concurrentFlow] and the synchronous [Listener]s.
     *
     * This function first notifies all asynchronous [Listener]s by emitting the event to the [concurrentFlow].
     * Each asynchronous [Listener] will execute its [Listener] function on a new coroutine.
     *
     * After notifying asynchronous [Listener]s, it executes the [Listener] functions of all synchronous [Listener]s.
     * An instant callback ([CallbackEvent]) can only be achieved by synchronous listening objects
     * as the concurrent listener will be executed "later".
     *
     * CAUTION: The returned [Event] may have not yet been processed by concurrent listeners.
     *
     * @param E The type of the event to be posted. This should be a subclass of Event.
     * @receiver The [Event] to be posted to the event flow.
     */
    @JvmStatic
    fun <E : Event> E.post(): E {
        concurrentFlow.tryEmit(this)
        executeListenerSynchronous()
        return this@post
    }

    /**
     * Posts an [Event] to the event flow and then applies the given [process] function to the event.
     *
     * This function first posts the event to the event flow by calling the [post] function.
     * After the event has been posted, it applies the [process] function to the event.
     *
     * CAUTION: The processed [Event] may have not yet been processed by concurrent listeners.
     *
     * @param E The type of the event to be posted. This should be a subclass of Event.
     * @param process A function to be applied to the event after it has been posted.
     */
    @JvmStatic
    fun <E : Event> E.post(process: E.() -> Unit) {
        post()
        process()
    }

    /**
     * Posts an [Event] to the event flow and then applies the given [process] function to the event
     * if it is not canceled.
     * If not it applies the [process] function to the event.
     *
     * CAUTION: The processed [Event] may have not yet been processed by concurrent listeners.
     *
     * @param E The type of the event to be posted. This should be a subclass of [Event] and implement [ICancellable].
     * @param process A function to be applied to the event after it has been posted if the [Event] is not canceled.
     */
    @JvmStatic
    fun <E> E.postChecked(process: E.() -> Unit) where E : Event, E : ICancellable {
        post()
        if (!isCanceled()) process()
    }

    /**
     * Unsubscribes from both synchronous and concurrent event flows for a specific [Event] type [T].
     *
     * This function removes the listeners associated with the specified event type from both synchronous and concurrent event flows.
     * After this function is called, the listeners of the specified event type will no longer be triggered when the event is dispatched.
     *
     * @param T The type of the event to unsubscribe from. This should be a subclass of Event.
     */
    inline fun <reified T : Event> unsubscribe() {
        syncListeners.remove(T::class)
        concurrentListeners.remove(T::class)
    }

    private fun Event.executeListenerSynchronous() {
        syncListeners[this::class]?.forEach { listener ->
            if (shouldNotNotify(listener, this)) return@forEach
            listener.execute(this)
        }
    }

    private fun Event.executeListenerConcurrently() {
        concurrentListeners[this::class]?.forEach { listener ->
            if (shouldNotNotify(listener, this)) return@forEach
            listener.execute(this)
        }
    }

    private fun shouldNotNotify(listener: Listener, event: Event) =
        listener.owner is Muteable
                && (listener.owner as Muteable).isMuted
                && !listener.alwaysListen
                || event is ICancellable && event.isCanceled()
}