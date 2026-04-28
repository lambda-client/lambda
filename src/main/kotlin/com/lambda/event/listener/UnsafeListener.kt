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
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.event.listener.SafeListener.Companion.listenConcurrently
import com.lambda.event.listener.SafeListener.Companion.listenOnce
import com.lambda.threading.runConcurrent
import com.lambda.util.Pointer
import com.lambda.util.collections.updatable
import com.lambda.util.selfReference
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * An [UnsafeListener] is a specialized type of [Listener] that operates without a [SafeContext].
 *
 * An [UnsafeListener] reacts to specific [Event]s happening within the system.
 * It has a [priority], an [owner], and a flag indicating whether it should [alwaysListen] to [Event]s,
 * even when the Object listening is [Muteable.isMuted].
 *
 * The [UnsafeListener] class is used to create [Listener]s that execute a given [function] without a [SafeContext].
 * This means that the [function] is executed in a context where certain safety conditions may not be met.
 *
 * The [SafeListener] will keep a reference to the last signal processed by the listener.
 * Allowing use cases where the last signal is needed.
 * ```kotlin
 * val lastPacketReceived by unsafeListener<PacketEvent.Receive.Pre>()
 *
 * unsafeListener<PacketEvent.Send.Pre> { event ->
 *     println("Last packet received: ${lastPacketReceived?.packet}")
 *     // prints the last packet received
 *     // prints null if no packet was received
 * }
 * ```
 *
 * @property priority The priority of the listener. Listeners with higher priority are executed first.
 * @property owner The owner of the listener. This is typically the object that created the listener.
 * @property alwaysListen If true, the listener will always be triggered, even if the owner is not enabled.
 * @property function The function to be executed when the event occurs. This function operates without a [SafeContext].
 */
class UnsafeListener<T : Event>(
    priorityProvider: () -> Int,
    override val owner: Any,
    override val alwaysListen: Boolean = false,
    val function: (T) -> Unit,
) : Listener<T>(), ReadOnlyProperty<Any?, T?> {
    override val priority = updatable(priorityProvider)

    /**
     * The last processed event signal.
     */
    private var lastSignal: T? = null

    override fun getValue(thisRef: Any?, property: KProperty<*>): T? = lastSignal

    /**
     * Executes the actions defined by this listener when the event occurs.
     */
    override fun execute(event: T) {
        lastSignal = event
        function(event)
    }

    companion object {
        /**
         * This function registers a new [UnsafeListener] for a generic [Event] type [T].
         * The [function] is executed on the same thread where the [Event] was dispatched.
         * The execution of the [function] is independent of the safety conditions of the context.
         * Use this function when you need to listen to an [Event] in a context that is not in-game.
         * For only in-game related contexts, use the [SafeListener.listen] function instead.
         *
         * Usage:
         * ```kotlin
         * listenUnsafe<MyEvent> { event ->
         *     println("Unsafe event received: $event")
         *     // no safe access to player or world
         * }
         *
         * listenUnsafe<MyEvent>(priority = 1) { event ->
         *     println("Unsafe event received before the previous listener: $event")
         * }
         * ```
         *
         * @param T The type of the event to listen for. This should be a subclass of Event.
         * @param priority The priority of the listener. Listeners with higher priority will be executed first.
         * @param alwaysListen If true, the listener will be executed even if it is muted.
         * @param function The function to be executed when the event is posted. This function should take an event of type T as a parameter.
         * @return The newly created and registered [UnsafeListener].
         */
        inline fun <reified T : Event> Any.listenUnsafe(
            noinline priority: () -> Int = ownerPriorityOr0Getter,
            alwaysListen: Boolean = false,
            noinline function: (T) -> Unit = {},
        ): UnsafeListener<T> {
            val listener = UnsafeListener<T>(priority, this, alwaysListen) { function(it) }

            EventFlow.syncListeners.subscribe(listener)

            return listener
        }

        /**
         * Registers a new [UnsafeListener] for a generic [Event] type [T].
         * The [function] is executed only once when the [Event] is dispatched.
         * This function should only be used when the [function] performs read actions on the game data.
         * For only in-game related contexts, use the [SafeListener.listenOnce] function instead.
         *
         * Usage:
         * ```kotlin
         * private val event by listenOnceUnsafe<MyEvent> { event ->
         *     println("Unsafe event received only once: $event")
         *     // no safe access to player or world
         *     // event is stored in the value
         *     // event is unsubscribed after execution
         * }
         * ```
         *
         * After the [function] is executed once, the [SafeListener] will be automatically unsubscribed.
         *
         * @param T The type of the event to listen for. This should be a subclass of Event.
         * @param priority The priority of the listener. Listeners with higher priority will be executed first.
         * @param alwaysListen If true, the listener will be executed even if it is muted.
         * @return The newly created and registered [UnsafeListener].
         */
        inline fun <reified T : Event> Any.listenOnceUnsafe(
            noinline priority: () -> Int = ownerPriorityOr0Getter,
            alwaysListen: Boolean = false,
            noinline function: (T) -> Boolean = { true },
        ): ReadWriteProperty<Any?, T?> {
            val pointer = Pointer<T>()

            val destroyable by selfReference<UnsafeListener<T>> {
                UnsafeListener(priority, this@listenOnceUnsafe, alwaysListen) { event ->
                    pointer.value = event

                    if (function(event)) {
                        val self by this@selfReference
                        EventFlow.syncListeners.unsubscribe(self)
                    }
                }
            }

            EventFlow.syncListeners.subscribe(destroyable)

            return pointer
        }

        /**
         * Registers a new [UnsafeListener] for a generic [Event] type [T].
         * The [function] is executed on a new thread running asynchronously to the game thread.
         * This function should only be used when the [function] performs read actions on the game data.
         * For only in-game related contexts, use the [SafeListener.listenConcurrently] function instead.
         *
         * Caution: Using this function to write to the game data can lead to race conditions. Therefore, it is recommended
         * to use this function only for read operations to avoid potential concurrency issues.
         *
         * Usage:
         * ```kotlin
         * listenUnsafeConcurrently<MyEvent> { event ->
         *     println("Concurrent event received: $event")
         *     // no safe access to player or world
         * }
         *
         * listenUnsafeConcurrently<MyEvent>(priority = 1) { event ->
         *     println("Concurrent event received before the previous listener: $event")
         * }
         * ```
         * @param T The type of the event to listen for. This should be a subclass of Event.
         * @param priority The priority of the listener. Listeners with higher priority will be executed first. The Default value is 0.
         * @param alwaysListen If true, the listener will be executed even if it is muted. The Default value is false.
         * @param function The function to be executed when the event is posted. This function should take a SafeContext and an event of type T as parameters.
         * @return The newly created and registered [UnsafeListener].
         */
        inline fun <reified T : Event> Any.listenConcurrentlyUnsafe(
            noinline priority: () -> Int = ownerPriorityOr0Getter,
            alwaysListen: Boolean = false,
            scheduler: CoroutineDispatcher = Dispatchers.Default,
            noinline function: suspend (T) -> Unit = {},
        ): UnsafeListener<T> {
            val listener = UnsafeListener<T>(priority, this, alwaysListen) { event ->
                runConcurrent(scheduler) {
                    function(event)
                }
            }

            EventFlow.concurrentListeners.subscribe(listener)

            return listener
        }
    }

    override fun toString() =
        "SafeListener(priority=$priority, owner=${owner::class.simpleName}, alwaysListen=$alwaysListen)"
}
