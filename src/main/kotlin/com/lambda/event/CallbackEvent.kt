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
