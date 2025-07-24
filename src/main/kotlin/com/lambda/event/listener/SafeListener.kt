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

package com.lambda.event.listener

import com.lambda.context.SafeContext
import com.lambda.event.Event
import com.lambda.event.EventFlow
import com.lambda.event.Muteable
import com.lambda.threading.runConcurrent
import com.lambda.threading.runGameScheduled
import com.lambda.threading.runSafe
import com.lambda.util.Pointer
import com.lambda.util.selfReference
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty


/**
 * A [SafeListener] is a specialized type of [Listener] that operates within a [SafeContext].
 *
 * A [SafeListener] reacts to specific [Event]s happening within the system.
 * It has a [priority], an [owner], and a flag indicating whether it should [alwaysListen] to [Event]s,
 * even when the Object listening is [Muteable.isMuted].
 *
 * The [SafeListener] class is used to create [Listener]s that execute a given [function] within a [SafeContext].
 * This ensures that the [function] is executed in a context where certain safety conditions are met.
 *
 * The [SafeListener] will keep a reference to the last signal processed by the listener.
 * Allowing use cases where the last signal is needed.
 * ```kotlin
 * val lastPacketReceived by listen<PacketEvent.Receive.Pre>()
 *
 * listen<PacketEvent.Send.Pre> { event ->
 *     println("Last packet received: ${lastPacketReceived?.packet}")
 *     // prints the last packet received
 *     // prints null if no packet was received
 * }
 * ```
 *
 * @property priority The priority of the listener. Listeners with higher priority are executed first.
 * @property owner The owner of the listener. This is typically the object that created the listener.
 * @property alwaysListen If true, the listener will always be triggered, even if the owner is not enabled.
 * @property function The function to be executed when the event occurs. This function operates within a [SafeContext].
 */
class SafeListener<T : Event>(
    override val priority: Int = 0,
    override val owner: Any,
    override val alwaysListen: Boolean = false,
    val function: SafeContext.(T) -> Unit,
) : Listener<T>(), ReadOnlyProperty<Any?, T?> {
    /**
     * The last processed event signal.
     */
    private var lastSignal: T? = null

    override fun getValue(thisRef: Any?, property: KProperty<*>): T? = lastSignal

    /**
     * Executes the actions defined by this listener when the event occurs.
     *
     * Note that running this function outside the game thread can
     * lead to race conditions when manipulating shared data.
     */
    override fun execute(event: T) {
        runSafe {
            lastSignal = event
            function(event)
        }
    }

    companion object {
        /**
         * This function registers a new [SafeListener] for a generic [Event] type [T].
         * The [function] is executed on the same thread where the [Event] was dispatched.
         * The [function] will only be executed when the context satisfies certain safety conditions.
         * These conditions are met when none of the following [SafeContext] properties are null:
         * - [SafeContext.world]
         * - [SafeContext.player]
         * - [SafeContext.interaction]
         * - [SafeContext.connection]
         *
         * This typically occurs when the user is in-game.
         *
         * Usage:
         * ```kotlin
         * listen<MyEvent> { event ->
         *     player.sendMessage("Event received: $event")
         * }
         *
         * listen<MyEvent>(priority = 1) { event ->
         *     player.sendMessage("Event received before the previous listener: $event")
         * }
         * ```
         *
         * @param T The type of the event to listen for. This should be a subclass of Event.
         * @param priority The priority of the listener. Listeners with higher priority will be executed first. The Default value is 0.
         * @param alwaysListen If true, the listener will be executed even if it is muted. The Default value is false.
         * @param function The function to be executed when the event is posted. This function should take a SafeContext and an event of type T as parameters.
         * @return The newly created and registered [SafeListener].
         */
        inline fun <reified T : Event> Any.listen(
            priority: Int = 0,
            alwaysListen: Boolean = false,
            noinline function: SafeContext.(T) -> Unit = {},
        ): SafeListener<T> {
            val listener = SafeListener<T>(priority, this, alwaysListen) { event ->
                runGameScheduled { function(event) }
            }

            EventFlow.syncListeners.subscribe(listener)

            return listener
        }

        /**
         * This function registers a new [SafeListener] for a generic [Event] type [T] with the given [KClass] instance to circumvent type erasure.
         * The [function] is executed on the same thread where the [Event] was dispatched.
         * The [function] will only be executed when the context satisfies certain safety conditions.
         * These conditions are met when none of the following [SafeContext] properties are null:
         * - [SafeContext.world]
         * - [SafeContext.player]
         * - [SafeContext.interaction]
         * - [SafeContext.connection]
         *
         * This typically occurs when the user is in-game.
         *
         * Usage:
         * ```kotlin
         * listen(MyEvent::class) { event ->
         *     player.sendMessage("Event received: $event")
         * }
         *
         * listen(MyEvent::class, priority = 1) { event ->
         *     player.sendMessage("Event received before the previous listener: $event")
         * }
         * ```
         *
         * @param kClass The KClass instance of covariant type [T] used to circumvent type erasure.
         * @param T The type of the event to listen for. This should be a subclass of Event.
         * @param priority The priority of the listener. Listeners with higher priority will be executed first. The Default value is 0.
         * @param alwaysListen If true, the listener will be executed even if it is muted. The Default value is false.
         * @param function The function to be executed when the event is posted. This function should take a SafeContext and an event of type T as parameters.
         * @return The newly created and registered [SafeListener].
         */
        fun <T : Event> Any.listen(
            kClass: KClass<out T>,
            priority: Int = 0,
            alwaysListen: Boolean = false,
            function: SafeContext.(T) -> Unit = {},
        ): SafeListener<T> {
            val listener = SafeListener<T>(priority, this, alwaysListen) { event ->
                runGameScheduled { function(event) }
            }

            EventFlow.syncListeners.subscribe(kClass, listener)

            return listener
        }

        /**
         * This function registers a new [SafeListener] for a generic [Event] type [T].
         * The [predicate] is executed on the same thread where the [Event] was dispatched.
         * The [predicate] will only be executed when the context satisfies certain safety conditions.
         * These conditions are met when none of the following [SafeContext] properties are null:
         * - [SafeContext.world]
         * - [SafeContext.player]
         * - [SafeContext.interaction]
         * - [SafeContext.connection]
         *
         * This typically occurs when the user is in-game.
         *
         * After the [predicate] is executed once, the [SafeListener] will be automatically unsubscribed.
         *
         * Usage:
         * ```kotlin
         * private val event by listenOnce<MyEvent> { event ->
         *     player.sendMessage("Event received only once: $event")
         *     // event is stored in the value
         *     // event is unsubscribed after execution
         * }
         * ```
         *
         * @param T The type of the event to listen for. This should be a subclass of Event.
         * @param priority The priority of the listener. Listeners with higher priority will be executed first. The Default value is 0.
         * @param alwaysListen If true, the listener will be executed even if it is muted. The Default value is false.
         * @return The newly created and registered [SafeListener].
         */
        inline fun <reified T : Event> Any.listenOnce(
            priority: Int = 0,
            alwaysListen: Boolean = false,
            noinline predicate: SafeContext.(T) -> Boolean = { true },
        ): ReadWriteProperty<Any?, T?> {
            val pointer = Pointer<T>()

            val destroyable by selfReference<SafeListener<T>> {
                SafeListener(priority, this@listenOnce, alwaysListen) { event ->
                    pointer.value = event

                    if (predicate(event)) {
                        val self by this@selfReference
                        EventFlow.syncListeners.unsubscribe(self)
                    }
                }
            }

            EventFlow.syncListeners.subscribe(destroyable)

            return pointer
        }

        /**
         * Registers a new [SafeListener] for a generic [Event] type [T].
         * The [function] is executed on a new thread running asynchronously to the game thread.
         * This function should only be used when the [function] performs read actions on the game data.
         *
         * Caution: Using this function to write to the game data can lead to race conditions. Therefore, it is recommended
         * to use this function only for read operations to avoid potential concurrency issues.
         *
         * Usage:
         * ```kotlin
         * listenConcurrently<MyEvent> { event ->
         *     println("Concurrent event received: $event")
         *     // no safe access to player or world
         * }
         *
         * listenConcurrently<MyEvent>(priority = 1) { event ->
         *     println("Concurrent event received before the previous listener: $event")
         * }
         * ```
         * @param T The type of the event to listen for. This should be a subclass of Event.
         * @param priority The priority of the listener. Listeners with higher priority will be executed first. The Default value is 0.
         * @param alwaysListen If true, the listener will be executed even if it is muted. The Default value is false.
         * @param function The function to be executed when the event is posted. This function should take a SafeContext and an event of type T as parameters.
         * @return The newly created and registered [SafeListener].
         */
        inline fun <reified T : Event> Any.listenConcurrently(
            priority: Int = 0,
            alwaysListen: Boolean = false,
            scheduler: CoroutineDispatcher = Dispatchers.Default,
            noinline function: suspend SafeContext.(T) -> Unit = {},
        ): SafeListener<T> {
            val listener = SafeListener<T>(priority, this, alwaysListen) { event ->
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
