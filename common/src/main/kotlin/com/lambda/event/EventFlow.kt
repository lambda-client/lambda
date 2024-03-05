package com.lambda.event

import com.lambda.event.listener.Listener
import com.lambda.runConcurrent
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNot
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListSet
import kotlin.reflect.KClass

object EventFlow {
    /**
     * [lambdaScope] is a [CoroutineScope] which is used to launch coroutines.
     *
     * It is defined with [Dispatchers.Default] which is optimized for CPU-intensive work
     * such as sorting large lists, doing complex calculations and similar.
     *
     * The [SupervisorJob] is used so that failure or cancellation of one child does not
     * lead to the failure or cancellation of the parent or its other children, which is
     * useful when you have multiple independent jobs running in parallel.
     */
    val lambdaScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val concurrentFlow = MutableSharedFlow<Event>(
        extraBufferCapacity = 1000,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val syncListeners = ConcurrentHashMap<KClass<*>, ConcurrentSkipListSet<Listener>>()
    val concurrentListeners = ConcurrentHashMap<KClass<*>, ConcurrentSkipListSet<Listener>>()

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
     * Posts an event to the event flow.
     *
     * This function first notifies all asynchronous listeners by emitting the event to the concurrent flow.
     * Each asynchronous listener will execute its listener function on a new thread.
     *
     * After notifying asynchronous listeners, it executes the listener functions of all synchronous listeners.
     * An instant callback can only be achieved by synchronous listening objects
     * as the concurrent listener will be executed "later".
     *
     * @param event The event to be posted to the event flow.
     */
    @JvmStatic
    fun post(event: Event) {
        concurrentFlow.tryEmit(event)
        event.executeListenerSynchronous()
    }

    private fun Event.executeListenerSynchronous() {
        syncListeners[this::class]?.forEach { listener ->
            if (this is ICancellable && this.isCanceled()) return
            listener.execute(this@executeListenerSynchronous)
        }
    }

    private fun Event.executeListenerConcurrently() {
        concurrentListeners[this::class]?.forEach { listener ->
            if (this is ICancellable && this.isCanceled()) return
            runConcurrent {
                listener.execute(this@executeListenerConcurrently)
            }
        }
    }
}