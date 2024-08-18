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
class Subscriber : ConcurrentHashMap<KClass<out Event>, ConcurrentSkipListSet<Listener<out Event>>>() {
    val defaultListenerSet: ConcurrentSkipListSet<Listener<out Event>>
        get() = ConcurrentSkipListSet(Listener.comparator.reversed())


    /** Allows a [Listener] to start receiving a specific type of [Event] */
    inline fun <reified T : Event> subscribe(listener: Listener<T>) =
        getOrPut(T::class) { defaultListenerSet }.add(listener)

    /** Allows a [Subscriber] to start receiving all [Event]s of another [Subscriber]. */
    infix fun subscribe(subscriber: Subscriber) {
        subscriber.forEach { (eventType, listeners) ->
            getOrPut(eventType) { defaultListenerSet }.addAll(listeners)
        }
    }

    /** Forgets about every [Listener]'s association to [eventType] */
    fun <T : Event> unsubscribe(eventType: KClass<T>) =
        remove(eventType)

    /** Allows a [Listener] to stop receiving a specific type of [Event] */
    inline fun <reified T : Event> unsubscribe(listener: Listener<T>) =
        getOrElse(T::class) { defaultListenerSet }.remove(listener)

    /** Allows a [Subscriber] to stop receiving all [Event]s of another [Subscriber] */
    infix fun unsubscribe(subscriber: Subscriber) {
        subscriber.forEach { (eventType, listeners) ->
            getOrElse(eventType) { defaultListenerSet }.removeAll(listeners)
        }
    }
}
