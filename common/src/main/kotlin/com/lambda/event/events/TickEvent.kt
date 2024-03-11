package com.lambda.event.events

import com.lambda.event.Event
import com.lambda.event.EventFlow

/**
 * An abstract class representing a [TickEvent] in the [EventFlow].
 *
 * A [TickEvent] is a type of [Event] that is triggered at each tick of the game loop.
 * It has two subclasses: [Pre] and [Post], which are triggered before and after the tick, respectively.
 *
 * The [TickEvent] class is designed to be extended by any class that needs to react to ticks.
 *
 * @see Pre
 * @see Post
 */
abstract class TickEvent : Event {
    /**
     * A class representing a [TickEvent] that is triggered before each tick of the game loop.
     */
    class Pre : TickEvent()

    /**
     * A class representing a [TickEvent] that is triggered after each tick of the game loop.
     */
    class Post : TickEvent()
}