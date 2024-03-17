package com.lambda.event

import com.lambda.event.listener.Listener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListSet
import kotlin.reflect.KClass

/**
 * The [Subscriber] class is a specialized [ConcurrentHashMap]
 * that manages sets of [Listener]s for different [Event] types.
 *
 * It provides methods to [subscribe] and [unsubscribe] [Listener]s and other [Subscriber]s.
 * It also allows for the merging of [Subscriber] [Set]s.
 *
 * @property defaultListenerSet A [ConcurrentSkipListSet] of [Listener]s, sorted in reverse order.
 */
class Subscriber : ConcurrentHashMap<KClass<*>, ConcurrentSkipListSet<Listener>>() {
    val defaultListenerSet: ConcurrentSkipListSet<Listener>
        get() = ConcurrentSkipListSet(Comparator.reverseOrder())


    /** Allows a [Listener] to start receiving a specific type of [Event] */
    inline fun <reified T : Event> subscribe(listener: Listener) =
        getOrPut(T::class) { defaultListenerSet }.add(listener)


    /** Forgets about every [Listener]s association to [eventType] */
    fun unsubscribe(eventType: KClass<*>) = remove(eventType)

    /** Allows a [Listener] to stop receiving a specific type of [Event] */
    fun unsubscribe(listener: Listener) {
        values.forEach { listeners ->
            listeners.remove(listener)
        }
    }

    /** Allows a [Subscriber] to start receiving all [Event]s of another [Subscriber]. */
    infix fun subscribe(subscriber: Subscriber) {
        subscriber.forEach { (eventType, listeners) ->
            getOrPut(eventType) { defaultListenerSet }.addAll(listeners)
        }
    }

    /** Allows a [Subscriber] to stop receiving all [Event]s of another [Subscriber] */
    infix fun unsubscribe(subscriber: Subscriber) {
        entries.removeAll { (eventType, listeners) ->
            subscriber[eventType]?.let { listeners.removeAll(it) }
            listeners.isEmpty()
        }
    }
}