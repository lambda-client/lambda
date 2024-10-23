/*
 * Copyright 2024 Lambda
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
 * The base interface for all [Event]s in the system.
 *
 * An [Event] represents a state or condition that can be listened for and acted upon.
 * It serves as a communication mechanism between different parts of an application.
 *
 * Subclasses of [Event] should encapsulate relevant information about the event's context and state.
 *
 * Implementations of this interface can be posted to the [EventFlow] for processing by registered listeners.
 *
 * Usage:
 * ```kotlin
 * class MyEvent(val message: String) : Event
 *
 * object MyListener {
 *     init {
 *         listener<MyEvent> { event -> println(event.message) }
 *     }
 * }
 *
 * EventFlow.post(MyEvent("Hello, world!"))
 * ```
 */
interface Event
