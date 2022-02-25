package com.lambda.client.event.eventbus

import com.lambda.client.event.listener.Listener
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListSet

/**
 * A concurrent implementation of [AbstractEventBus]
 */
open class ConcurrentEventBus : AbstractEventBus() {
    final override val subscribedListeners = ConcurrentHashMap<Class<*>, MutableSet<Listener<*>>>()

    override fun newSet() = ConcurrentSkipListSet<Listener<*>>(Comparator.reverseOrder())
}

/**
 * A concurrent implementation of [AbstractEventBus] and [IMultiEventBus]
 */
open class MultiEventBus : ConcurrentEventBus(), IMultiEventBus {
    private val subscribedEventBus = Collections.newSetFromMap<IEventBus>(ConcurrentHashMap())

    final override fun subscribe(eventBus: IEventBus) {
        subscribedEventBus.add(eventBus)
    }

    final override fun unsubscribe(eventBus: IEventBus) {
        subscribedEventBus.remove(eventBus)
    }

    final override fun post(event: Any) {
        super.post(event)
        for (eventBus in subscribedEventBus) eventBus.post(event)
    }
}