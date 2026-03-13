/*
 * Copyright 2026 Lambda
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

package com.lambda.event.listener

import com.lambda.event.Event
import com.lambda.event.EventFlow
import com.lambda.event.Muteable
import com.lambda.event.OwnerPriority
import com.lambda.module.Module
import com.lambda.util.collections.Updatable

/**
 * An abstract class representing a [Listener] in the [Event] system ([EventFlow]).
 *
 * A [Listener] is an entity that reacts to specific [Event]s happening within the system.
 * It has a [priority], an [owner],
 * and a flag indicating whether it should always listen to [Event]s,
 * even when the Object listening is [Muteable.isMuted] (e.g.: If it is a [Module] and not [Module.isEnabled]).
 *
 * The [Listener] class is not directly used to create listeners.
 * Instead, it serves as a base for the [SafeListener] and [UnsafeListener] classes,
 * which will trigger in different contexts (see [SafeListener] and [UnsafeListener]).
 *
 * The extending class needs to implement the [execute] method,
 * which defines the actions to be taken when the [Event] occurs.
 *
 * [Listener]s can be sorted based on their [priority],
 * if two [Listener]s have the same [priority], their order is determined by their [hashCode].
 *
 * @property priority The priority of the [Listener]. [Listener]s with higher [priority] are executed first.
 * @property owner The owner of the [Listener]. This is typically the object that created the [Listener].
 * @property alwaysListen If true, the [Listener] will always be triggered, even if the [owner] is [Muteable.isMuted].
 */
abstract class Listener<T : Event> : Comparable<Listener<T>> {
    abstract val priority: Updatable<Int>
    abstract val owner: Any
    abstract val alwaysListen: Boolean

    /**
     * Executes the actions defined by this listener when the event occurs.
     *
     * @param event The event that triggered this listener.
     */
    abstract fun execute(event: T)

    override fun compareTo(other: Listener<T>) =
        comparator.compare(this, other)

    companion object {
        val comparator = compareBy<Listener<out Event>> {
            it.priority.value
        }.thenBy {
            // Hashcode is needed because ConcurrentSkipListSet handles insertion based on compareTo
            it.hashCode()
        }

        val Any.ownerPriorityOr0Getter
            get() = (this as? OwnerPriority)?.let { { ownerPriority } } ?: { 0 }
    }
}
