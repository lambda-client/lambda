/*
 * Copyright 2025 Lambda
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

	/**
	 * Allows a [Listener] to start receiving a specific type of [Event]'s [KClass implementation](KC).
	 *
	 * This should only be used in cases where the type of the event is erased.
	 */
	fun <T : Event> subscribe(kClass: KClass<out T>, listener: Listener<T>) =
		getOrPut(kClass) { defaultListenerSet }.add(listener)

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

	/**
	 * Unsubscribes all listeners associated with the current instance (the caller object).
	 *
	 * This method iterates over all values in the `Subscriber`'s map and removes listeners
	 * whose `owner` property matches the caller object.
	 *
	 * Use this method when you want to clean up listeners that were registered with the
	 * current instance, preventing further event notifications.
	 */
	fun unsubscribe(owner: Any) {
		values.forEach { it.removeAll { listener -> listener.owner == owner } }
	}

	/** Allows a [Subscriber] to stop receiving all [Event]s of another [Subscriber] */
	infix fun unsubscribe(subscriber: Subscriber) {
		subscriber.forEach { (eventType, listeners) ->
			getOrElse(eventType) { defaultListenerSet }.removeAll(listeners)
		}
	}
}
