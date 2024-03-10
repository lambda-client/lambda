package com.lambda.common.event

import com.lambda.common.event.listener.Listener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListSet
import kotlin.reflect.KClass

class Subscriber : ConcurrentHashMap<KClass<*>, ConcurrentSkipListSet<Listener>>() {
    inline fun <reified T : Event> subscribe(listener: Listener) =
        getOrPut(T::class) {
            ConcurrentSkipListSet(Comparator.reverseOrder())
        }.add(listener)

    fun unsubscribe(eventType: KClass<*>) = remove(eventType)

    fun unsubscribe(listener: Listener) {
        values.forEach { listeners ->
            listeners.remove(listener)
        }
    }

    infix fun subscribe(subscriber: Subscriber) {
        subscriber.forEach { (eventType, listeners) ->
            getOrPut(eventType) {
                ConcurrentSkipListSet(Comparator.reverseOrder())
            }.addAll(listeners)
        }
    }

    infix fun unsubscribe(subscriber: Subscriber) {
        entries.removeAll { (eventType, listeners) ->
            subscriber[eventType]?.let { listeners.removeAll(it) }
            listeners.isEmpty()
        }
    }
}
