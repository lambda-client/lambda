package com.lambda.event.events

import com.lambda.event.Event
import com.lambda.event.EventFlow
import com.lambda.event.events.TickEvent.Post
import com.lambda.event.events.TickEvent.Pre

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
     * A class representing a [TickEvent] that is triggered before each tick of the tick loop.
     */
    class Pre : TickEvent()

    /**
     * A class representing a [TickEvent] that is triggered after each tick of the tick loop.
     */
    class Post : TickEvent()

    /**
     * A class representing a [TickEvent] that is triggered on each tick of the game loop.
     */
    abstract class GameLoop : TickEvent() {
        /**
         * A class representing a [TickEvent.Player] that is triggered before each tick of the game loop.
         */
        class Pre : TickEvent()

        /**
         * A class representing a [TickEvent.Player] that is triggered after each tick of the game loop.
         */
        class Post : TickEvent()
    }

    /**
     * A class representing a [TickEvent] that is triggered when the player gets ticked.
     */
    abstract class Player : TickEvent() {
        /**
         * A class representing a [TickEvent.Player] that is triggered before each player tick.
         */
        class Pre : Player()

        /**
         * A class representing a [TickEvent.Player] that is triggered after each player tick.
         */
        class Post : Player()
    }
}
