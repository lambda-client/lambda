package com.lambda.event.listener

import com.lambda.event.Event
import com.lambda.event.EventFlow
import java.util.concurrent.ConcurrentSkipListSet

data class UnsafeListener(
    override val priority: Int,
    val function: (Event) -> Unit
) : Listener() {
    override fun execute(event: Event) {
//        if (!mc.isOnThread) {
//            LOG.warn("""
//                    Event ${this::class.simpleName} executed outside of the game thread.
//                    This can lead to race conditions when manipulating game data.
//                    Consider moving the execution to the game thread using runSafeOnGameThread { ... } or runOnGameThread { ... }.
//                """.trimIndent())
//        }
        function(event)
    }

    companion object {
        /**
         * This function registers a new [UnsafeListener] for a generic [Event] type [T].
         * The [function] is executed on the same thread where the [Event] was dispatched.
         * The execution of the [function] is independent of the safety conditions of the context.
         * Use this function when you need to listen to an [Event] in a context that is not in-game.
         * For only in-game related contexts, use the [listener] function instead.
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
         * @param priority The priority of the listener. Listeners with higher priority will be executed first. Default value is 0.
         * @param function The function to be executed when the event is posted. This function should take an event of type T as a parameter.
         */
        inline fun <reified T : Event> unsafeListener(priority: Int = 0, noinline function: (T) -> Unit) {
            EventFlow.syncListeners.getOrPut(T::class) {
                ConcurrentSkipListSet(Comparator.reverseOrder())
            }.add(UnsafeListener(priority) { event ->
                function(event as T)
            })
        }

        /**
         * Registers a new [UnsafeListener] for a generic [Event] type [T].
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
        inline fun <reified T : Event> unsafeConcurrentListener(priority: Int = 0, noinline function: (T) -> Unit) {
            EventFlow.concurrentListeners.getOrPut(T::class) {
                ConcurrentSkipListSet(Comparator.reverseOrder())
            }.add(UnsafeListener(priority) { event ->
                function(event as T)
            })
        }
    }
}
