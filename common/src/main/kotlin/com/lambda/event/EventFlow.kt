/*
 * Copyright 2024 Lambda
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

    /**
     * [concurrentFlow] is a [MutableSharedFlow] of [Event]s with a buffer capacity to handle event emissions.
     *
     * Events emitted to this flow are processed by concurrent listeners, allowing for parallel event handling.
     *
     * The buffer overflow strategy is set to [BufferOverflow.DROP_OLDEST], meaning that when the buffer is full,
     * the oldest event will be dropped to accommodate a new event.
     */
    val concurrentFlow = MutableSharedFlow<Event>(
        extraBufferCapacity = 10000,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /**
     * [syncListeners] is a [Subscriber] that manages synchronous listeners.
     *
     * These listeners will be executed immediately when an event is posted, allowing for immediate responses to events.
     * The [syncListeners] are stored in a [Subscriber] object, which is a specialized [ConcurrentHashMap] that manages sets of [Listener]s for different [Event] types.
     */
    val syncListeners = Subscriber()

    /**
     * [concurrentListeners] is a [Subscriber] that manages asynchronous listeners.
     *
     * These listeners will be executed in parallel, each on a dedicated coroutine,
     * allowing for concurrent processing of events.
     * The [concurrentListeners] are stored in a [Subscriber] object, which is a specialized [ConcurrentHashMap] that manages sets of [Listener]s for different [Event] types.
     */
    val concurrentListeners = Subscriber()

    fun Any.unsubscribe() {
        syncListeners.unsubscribe(this)
        concurrentListeners.unsubscribe(this)
    }

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

    /**
     * Suspends until an event of type [E] is received that satisfies the given [predicate].
     *
     * @param E The type of the event to wait for. This should be a subclass of [Event].
     * @param predicate A lambda to test if the event satisfies the condition.
     * @return The first event that matches the predicate.
     */
    suspend inline fun <reified E : Event> blockUntilEvent(
        noinline predicate: SafeContext.(E) -> Boolean = { true },
    ) = concurrentFlow.filterIsInstance<E>().first {
        runSafe {
            predicate(it)
        } ?: false
    }

    /**
     * Suspends until an event of type [E] is received that satisfies the given [predicate],
     * or until the specified [timeout] occurs.
     *
     * @param E The type of the event to wait for. This should be a subclass of [Event].
     * @param timeout The maximum time to wait for the event, in milliseconds.
     * @param predicate A lambda to test if the event satisfies the condition.
     * @return The first event that matches the predicate or throws a timeout exception if not found.
     */
    suspend inline fun <reified E : Event> blockUntilEvent(
        timeout: Long,
        noinline predicate: (E) -> Boolean = { true },
    ) = runBlocking {
        withTimeout(timeout) {
            concurrentFlow.filterIsInstance<E>().first(predicate)
        }
    }

    /**
     * Suspends until an event of type [E] is received that satisfies the given [predicate].
     *
     * This method is "unsafe" in the sense that it does not execute the predicate within a [SafeContext].
     *
     * @param E The type of the event to wait for. This should be a subclass of [Event].
     * @param predicate A lambda to test if the event satisfies the condition.
     * @return The first event that matches the predicate.
     */
    suspend inline fun <reified E : Event> blockUntilUnsafeEvent(
        noinline predicate: (E) -> Boolean = { true },
    ) = concurrentFlow.filterIsInstance<E>().first(predicate)

    /**
     * Returns a [Flow] of events of type [E] that satisfy the given [predicate].
     *
     * @param E The type of the event to filter. This should be a subclass of [Event].
     * @param predicate A lambda to test if the event satisfies the condition.
     * @return A [Flow] emitting events that match the predicate.
     */
    suspend inline fun <reified E : Event> collectEvents(
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

    /**
     * Executes the listeners for the current event type synchronously.
     *
     * This method retrieves the list of synchronous listeners for the event's class
     * and invokes their [Listener.execute] method if the listener should be notified.
     *
     * @receiver The current event for which listeners are to be executed.
     * @param T The type of the event being handled.
     */
    private fun <T : Event> T.executeListenerSynchronous() {
        syncListeners[this::class]?.forEach {
            @Suppress("UNCHECKED_CAST")
            val listener = it as? Listener<T> ?: return@forEach
            if (shouldNotNotify(listener, this)) return@forEach
            listener.execute(this)
        }
    }

    /**
     * Executes the listeners for the current event type concurrently.
     *
     * This method retrieves the list of concurrent listeners for the event's class
     * and invokes their [Listener.execute] method if the listener should be notified.
     * Each listener is executed on the same coroutine scope.
     *
     * @receiver The current event for which listeners are to be executed.
     * @param T The type of the event being handled.
     */
    private fun <T : Event> T.executeListenerConcurrently() {
        concurrentListeners[this::class]?.forEach {
            @Suppress("UNCHECKED_CAST")
            val listener = it as? Listener<T> ?: return@forEach
            if (shouldNotNotify(listener, this)) return@forEach
            listener.execute(this)
        }
    }

    /**
     * Determines whether a given [listener] should be notified about an [event].
     *
     * A listener should not be notified if:
     * - The listener's owner is a [Muteable] and is currently muted, unless the listener is set to [alwaysListen].
     * - The event is cancellable and has been canceled.
     *
     * @param listener The listener to check.
     * @param event The event being processed.
     * @param T The type of the event.
     * @return `true` if the listener should not be notified, `false` otherwise.
     */
    private fun <T : Event> shouldNotNotify(listener: Listener<T>, event: Event) =
        listener.owner is Muteable
                && (listener.owner as Muteable).isMuted
                && !listener.alwaysListen
                || event is ICancellable && event.isCanceled()
}
