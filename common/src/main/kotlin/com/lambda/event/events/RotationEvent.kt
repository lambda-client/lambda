package com.lambda.event.events

import com.lambda.event.Event
import com.lambda.event.callback.Cancellable
import com.lambda.event.callback.ICancellable
import com.lambda.interaction.RotationManager
import com.lambda.interaction.rotation.RotationContext

abstract class RotationEvent : Event {
    /**
     * This event allows listeners to register a rotation request to be executed that tick.
     *
     * Only one rotation can "win" each tick
     *
     * CAUTION: The listener with the LOWEST priority will win as it is the last to override the context.
     *
     * @property context The rotation context that listeners can set. Only one rotation can "win" each tick.
     */
    class Update : RotationEvent(), ICancellable by Cancellable() {
        // Always check if baritone wants to rotate as well
        var context = RotationManager.BaritoneProcessor.baritoneContext
    }

    /**
     * This event allows listeners to modify the yaw relative to which the movement input is going to be constructed
     *
     * @property strafeYaw The angle at which the player will move when pressing W
     * Changing this value will never force the anti cheat to flag you because RotationManager is designed to modify the key input instead
     */
    class Strafe(var strafeYaw: Double) : RotationEvent()

    class Post(val context: RotationContext) : RotationEvent()
}
