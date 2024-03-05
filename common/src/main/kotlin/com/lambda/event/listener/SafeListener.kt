package com.lambda.event.listener

import com.lambda.context.SafeContext
import com.lambda.event.Event
import com.lambda.event.EventFlow
import com.lambda.runSafe
import java.util.concurrent.ConcurrentSkipListSet

class SafeListener(
    override val priority: Int = 0,
    val function: SafeContext.(Event) -> Unit
) : Listener() {
    override fun execute(event: Event) {
        runSafe {
//            if (!mc.isOnThread) {
//                LOG.warn("""
//                    Event ${this::class.simpleName} executed outside the game thread.
//                    This can lead to race conditions when manipulating game data.
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
         * These conditions are met when none of the following [ClientContext] properties are null:
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
         * @param priority The priority of the listener. Listeners with higher priority will be executed first. Default value is 0.
         * @param function The function to be executed when the event is posted. This function should take a SafeContext and an event of type T as parameters.
         */
        inline fun <reified T : Event> listener(priority: Int = 0, noinline function: SafeContext.(T) -> Unit) {
            EventFlow.syncListeners.getOrPut(T::class) {
                ConcurrentSkipListSet(Comparator.reverseOrder())
            }.add(SafeListener(priority) { event ->
                function(event as T)
            })
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
         * @param priority The priority of the listener. Listeners with higher priority will be executed first. Default value is 0.
         * @param function The function to be executed when the event is posted. This function should take a SafeContext and an event of type T as parameters.
         */
        inline fun <reified T : Event> concurrentListener(priority: Int = 0, noinline function: SafeContext.(T) -> Unit) {
            EventFlow.concurrentListeners.getOrPut(T::class) {
                ConcurrentSkipListSet(Comparator.reverseOrder())
            }.add(SafeListener(priority) { event ->
                function(event as T)
            })
        }
    }
}
