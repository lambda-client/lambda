package com.lambda.event

/**
 * The [Muteable] interface represents any listening object that may need
 * to temporarily stop listening to events.
 *
 * Implementing this interface allows an object to
 * control its listening state through the [isMuted] property.
 * When [isMuted] is `true`, the object will not receive or process events.
 *
 * This can be useful in scenarios where an object's event handling behavior should be paused, for example,
 * when the object is in a certain state or when a specific condition is met.
 *
 * @property isMuted A flag indicating whether the object is currently muted.
 */
interface Muteable {
    val isMuted: Boolean
}