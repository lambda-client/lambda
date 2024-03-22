package com.lambda.event

import com.lambda.Lambda.LOG
import com.lambda.event.cancellable.Cancellable
import com.lambda.event.cancellable.ICancellable
import com.lambda.event.listener.Listener
import com.lambda.threading.runConcurrent
import com.lambda.util.Communication.info
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNot

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
    private val concurrentFlow = MutableSharedFlow<Event>(
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
     * @param event The [Event] to be posted to the event flow.
     */
    @JvmStatic
    fun post(event: Event) {
        concurrentFlow.tryEmit(event)
        event.executeListenerSynchronous()
    }

    /**
     * Posts a [cancellable] [Event] to the event flow and returns the [Event].
     *
     * This function is a variant of the [post] function specifically for [ICancellable] [Event]s.
     * It posts the [Event] to the event flow,
     * runs it through the synchronous set of listeners ([syncListeners]), and then returns the [Event].
     * This is useful as [ICancellable] [Event]s are often checked after being processed by the [Listener]s.
     *
     * The returned event is guaranteed to be of the same type as the input,
     * thanks to the type parameter [T] which is a subtype of both [Event] and [ICancellable].
     *
     * @param cancellable The cancellable [Event] to be posted to the event flow.
     * @return The same [ICancellable] [Event] after being processed by the [Listener]s.
     */
    @JvmStatic
    fun <T> post(cancellable: T) : T where T : Event, T : ICancellable {
        post(cancellable as Event)
        return cancellable
    }

    @JvmStatic
    fun <T> post(cancellable: T, process: T.() -> Unit) where T : Event, T : ICancellable {
        val event = post(cancellable)
        process(event)
    }

    @JvmStatic
    fun <T> postChecked(cancellable: T, process: T.() -> Unit) where T : Event, T : ICancellable {
        val event = post(cancellable)
        if (!event.isCanceled()) process(event)
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
            runConcurrent {
                listener.execute(this)
            }
        }
    }

    private fun shouldNotNotify(listener: Listener, event: Event) =
        listener.owner is Muteable
            && (listener.owner as Muteable).isMuted
            && !listener.alwaysListen
            || event is ICancellable && event.isCanceled()
}