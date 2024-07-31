package com.lambda.event.listener

import com.lambda.context.SafeContext
import com.lambda.event.Event
import com.lambda.event.EventFlow
import com.lambda.event.Muteable
import com.lambda.task.Task
import com.lambda.threading.runConcurrent
import com.lambda.threading.runSafe
import com.lambda.util.selfReference


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
 * @property priority The priority of the listener. Listeners with higher priority are executed first.
 * @property owner The owner of the listener. This is typically the object that created the listener.
 * @property alwaysListen If true, the listener will always be triggered, even if the owner is not enabled.
 * @property function The function to be executed when the event occurs. This function operates within a [SafeContext].
 */
class SafeListener(
    override val priority: Int = 0,
    override val owner: Any,
    override val alwaysListen: Boolean = false,
    val function: SafeContext.(Event) -> Unit,
) : Listener() {
    override fun execute(event: Event) {
        runSafe {
//            if (!mc.isOnThread) {
//                LOG.warn("""
//                    Event ${this::class.simpleName} executed outside the game thread.
//                    This can lead to race conditions when manipulating shared data.
//                    Consider moving the execution to the game thread using runSafeOnGameThread { ... } or runOnGameThread { ... }.
//                """.trimIndent())
//            }
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
         * listener<MyEvent> { event ->
         *     player.sendMessage("Event received: $event")
         * }
         *
         * listener<MyEvent>(priority = 1) { event ->
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
        inline fun <reified T : Event> Any.listener(
            priority: Int = 0,
            alwaysListen: Boolean = false,
            noinline function: SafeContext.(T) -> Unit,
        ): SafeListener {
            val listener = SafeListener(priority, this, alwaysListen) { event ->
                function(event as T)
            }

            EventFlow.syncListeners.subscribe<T>(listener)

            return listener
        }

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
         * After the [function] is executed once, the [SafeListener] will be automatically unsubscribed.
         *
         * Usage:
         * ```kotlin
         * listenerOnce<MyEvent> { event ->
         *     player.sendMessage("Event received: $event")
         * }
         *
         * listenerOnce<MyEvent>(priority = 1) { event ->
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
        inline fun <reified T : Event> Any.listenOnce(
            priority: Int = 0,
            alwaysListen: Boolean = false,
            noinline function: SafeContext.(T) -> Unit,
        ): SafeListener {
            val destroyable by selfReference<SafeListener> {
                SafeListener(priority, this@listenOnce, alwaysListen) { event ->
                    function(event as T)
                    EventFlow.syncListeners.unsubscribe(self)
                }
            }

            EventFlow.syncListeners.subscribe<T>(destroyable)

            return destroyable
        }

        /**
         * Registers a new [SafeListener] for a generic [Event] type [T] within the context of a [Task].
         * The [function] is executed on the same thread where the [Event] was dispatched.
         * The [function] will only be executed when the context satisfies certain safety conditions.
         * These conditions are met when none of the following [SafeContext] properties are null:
         * - [SafeContext.world]
         * - [SafeContext.player]
         * - [SafeContext.interaction]
         * - [SafeContext.connection]
         *
         * Usage:
         * ```kotlin
         * myTask.listener<MyEvent> { event ->
         *     player.sendMessage("Event received: $event")
         * }
         *
         * myTask.listener<MyEvent>(priority = 1) { event ->
         *     player.sendMessage("Event received before the previous listener: $event")
         * }
         * ```
         *
         * @param T The type of the event to listen for.
         * This should be a subclass of Event.
         * @param priority The priority of the listener.
         * Listeners with higher priority will be executed first.
         * The Default value is 0.
         * @param alwaysListen If true, the listener will be executed even if it is muted. The Default value is false.
         * @param function The function to be executed when the event is posted.
         * This function should take a SafeContext and an event of type T as parameters.
         * @return The newly created and registered [SafeListener].
         */
        inline fun <reified T : Event> Task<*>.listener(
            priority: Int = 0,
            alwaysListen: Boolean = false,
            noinline function: SafeContext.(T) -> Unit,
        ): SafeListener {
            val listener = SafeListener(priority, this, alwaysListen) { event ->
                function(event as T) // ToDo: run function always on game thread
            }

            syncListeners.subscribe<T>(listener)

            return listener
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
         * @return The newly created and registered [SafeListener].
         */
        inline fun <reified T : Event> Any.concurrentListener(
            priority: Int = 0,
            alwaysListen: Boolean = false,
            noinline function: suspend SafeContext.(T) -> Unit,
        ): SafeListener {
            val listener = SafeListener(priority, this, alwaysListen) { event ->
                runConcurrent {
                    function(event as T)
                }
            }

            EventFlow.concurrentListeners.subscribe<T>(listener)

            return listener
        }
    }

    override fun toString() =
        "SafeListener(priority=$priority, owner=${owner::class.simpleName}, alwaysListen=$alwaysListen)"
}
