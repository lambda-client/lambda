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