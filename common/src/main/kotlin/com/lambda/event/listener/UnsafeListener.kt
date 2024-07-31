package com.lambda.event.listener

import com.lambda.context.SafeContext
import com.lambda.event.Event
import com.lambda.event.EventFlow
import com.lambda.event.Muteable
import com.lambda.event.listener.SafeListener.Companion.concurrentListener
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.event.listener.SafeListener.Companion.receiveNext
import com.lambda.util.selfReference

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
    override val priority: Int,
    override val owner: Any,
    override val alwaysListen: Boolean = false,
    val function: (T) -> Unit,
) : Listener<T>() {
    private var lastSignal: T? = null
    operator fun getValue(thisRef: Any?, property: Any?): T? = lastSignal

    override fun execute(event: T) {
//        if (!mc.isOnThread) {
//            LOG.warn("""
//                    Event ${this::class.simpleName} executed outside the game thread.
//                    This can lead to race conditions when manipulating game data.
//                    Consider moving the execution to the game thread using runSafeOnGameThread { ... } or runOnGameThread { ... }.
//                """.trimIndent())
//        }

        lastSignal = event
        function(event)
    }

    companion object {
        /**
         * This function registers a new [UnsafeListener] for a generic [Event] type [T].
         * The [function] is executed on the same thread where the [Event] was dispatched.
         * The execution of the [function] is independent of the safety conditions of the context.
         * Use this function when you need to listen to an [Event] in a context that is not in-game.
         * For only in-game related contexts, use the [SafeListener.listener] function instead.
         *
         * Usage:
         * ```kotlin
         * unsafeListener<MyEvent> { event ->
         *     println("Unsafe event received: $event")
         *     // no safe access to player or world
         * }
         *
         * unsafeListener<MyEvent>(priority = 1) { event ->
         *     println("Unsafe event received before the previous listener: $event")
         * }
         * ```
         *
         * @param T The type of the event to listen for. This should be a subclass of Event.
         * @param priority The priority of the listener. Listeners with higher priority will be executed first. The Default value is 0.
         * @param alwaysListen If true, the listener will be executed even if it is muted. The Default value is false.
         * @param function The function to be executed when the event is posted. This function should take an event of type T as a parameter.
         * @return The newly created and registered [UnsafeListener].
         */
        inline fun <reified T : Event> Any.unsafeListener(
            priority: Int = 0,
            alwaysListen: Boolean = false,
            noinline function: (T) -> Unit = {},
        ): UnsafeListener<T> {
            val listener = UnsafeListener<T>(priority, this, alwaysListen) { event ->
                function(event)
            }

            EventFlow.syncListeners.subscribe<T>(listener)

            return listener
        }

        /**
         * Registers a new [UnsafeListener] for a generic [Event] type [T].
         * The [function] is executed only once when the [Event] is dispatched.
         * This function should only be used when the [function] performs read actions on the game data.
         * For only in-game related contexts, use the [SafeListener.receiveNext] function instead.
         *
         * Usage:
         * ```kotlin
         * private val event by unsafeReceiveNext<MyEvent> { event ->
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
         * @param priority The priority of the listener. Listeners with higher priority will be executed first. The Default value is 0.
         * @param alwaysListen If true, the listener will be executed even if it is muted. The Default value is false.
         * @param function The function to be executed when the event is posted. This function should take an event of type T as a parameter.
         * @return The newly created and registered [UnsafeListener].
         */
        inline fun <reified T : Event> Any.unsafeReceiveNext(
            priority: Int = 0,
            alwaysListen: Boolean = false,
            noinline function: (T) -> Unit = {},
        ): UnsafeListener<T> {
            val destroyable by selfReference<UnsafeListener<T>> {
                UnsafeListener(priority, this@unsafeReceiveNext, alwaysListen) { event ->
                    function(event)
                    EventFlow.syncListeners.unsubscribe(self)
                }
            }

            EventFlow.syncListeners.subscribe<T>(destroyable)

            return destroyable
        }

        /**
         * Registers a new [UnsafeListener] for a generic [Event] type [T].
         * The [function] is executed on a new thread running asynchronously to the game thread.
         * This function should only be used when the [function] performs read actions on the game data.
         * For only in-game related contexts, use the [SafeListener.concurrentListener] function instead.
         *
         * Caution: Using this function to write to the game data can lead to race conditions. Therefore, it is recommended
         * to use this function only for read operations to avoid potential concurrency issues.
         *
         * Usage:
         * ```kotlin
         * concurrentListener<MyEvent> { event ->
         *     println("Concurrent event received: $event")
         *     // no safe access to player or world
         * }
         *
         * concurrentListener<MyEvent>(priority = 1) { event ->
         *     println("Concurrent event received before the previous listener: $event")
         * }
         * ```
         * @param T The type of the event to listen for. This should be a subclass of Event.
         * @param priority The priority of the listener. Listeners with higher priority will be executed first. The Default value is 0.
         * @param alwaysListen If true, the listener will be executed even if it is muted. The Default value is false.
         * @param function The function to be executed when the event is posted. This function should take a SafeContext and an event of type T as parameters.
         * @return The newly created and registered [UnsafeListener].
         */
        inline fun <reified T : Event> Any.unsafeConcurrentListener(
            priority: Int = 0,
            alwaysListen: Boolean = false,
            noinline function: (T) -> Unit = {},
        ): UnsafeListener<T> {
            val listener = UnsafeListener<T>(priority, this, alwaysListen) { event -> function(event) }

            EventFlow.concurrentListeners.subscribe<T>(listener)

            return listener
        }
    }
}
