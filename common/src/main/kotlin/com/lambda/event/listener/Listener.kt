package com.lambda.event.listener

import com.lambda.event.Event

abstract class Listener : Comparable<Listener> {
    abstract val priority: Int
    abstract val owner: Any // ToDo: Evaluate if this is even needed

    abstract fun execute(event: Event)

    override fun compareTo(other: Listener) =
        compareBy<Listener> {
            it.priority
        }.thenBy {
            // Needed because ConcurrentSkipListSet handles insertion based on compareTo
            it.hashCode()
        }.compare(this, other)
}