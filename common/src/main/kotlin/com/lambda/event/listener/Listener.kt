package com.lambda.event.listener

import com.lambda.event.Event
import com.lambda.event.EventFlow

abstract class Listener : Comparable<Listener> {
    abstract val priority: Int

    abstract fun execute(event: Event)

    override fun compareTo(other: Listener): Int {
        return compareBy<Listener> { it.priority }.thenBy { it.hashCode() }.compare(this, other)
    }

    companion object {
        /**
         * Unsubscribes the [SafeListener] from both synchronous and concurrent event flows for a specific [Event] type [T].
         *
         * This function removes the listeners associated with the specified event type from both synchronous and concurrent event flows.
         * After this function is called, the listeners of the specified event type will no longer be triggered when the event is dispatched.
         *
         * @param T The type of the event to unsubscribe from. This should be a subclass of Event.
         */
        inline fun <reified T : Event> unsubscribe() {
            EventFlow.syncListeners.remove(T::class)
            EventFlow.concurrentListeners.remove(T::class)
        }
    }
}