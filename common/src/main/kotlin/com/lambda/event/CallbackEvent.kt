package com.lambda.event

/**
 * Represents an event that supports providing feedback after being posted to the [EventFlow].
 *
 * A [CallbackEvent] can be modified and listened to only from synchronous listeners ([EventFlow.syncListeners]).
 * This allows the event to provide feedback after its invocation.
 *
 * Implement this interface when you need an event to return data back to the caller after being processed by the listeners.
 *
 * Usage:
 * ```kotlin
 * class MyCallbackEvent : CallbackEvent {
 *     var result: ResultType? = null
 * }
 *
 * object MyListener {
 *    init {
 *        // will be executed on the game thread synchronously
 *        listener<MyCallbackEvent> { event ->
 *            // will be readable after the event is posted
 *            event.result = processEvent(event)
 *            // ...
 *        }
 *
 *        // will be executed on a dedicated thread asynchronously
 *        concurrentListener<MyCallbackEvent> { event ->
 *             // will not be readable after the event is posted as it takes time to execute
 *             event.result = processEvent(event)
 *        }
 *    }
 * }
 *
 * val event = MyCallbackEvent()
 * EventFlow.post(event)
 * val result = event.result
 * ```
 */
interface CallbackEvent : Event