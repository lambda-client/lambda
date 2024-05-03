package com.lambda.event.events

import com.lambda.event.Event
import com.lambda.event.callback.Cancellable
import com.lambda.event.callback.ICancellable
import net.minecraft.client.input.Input

abstract class MovementEvent : Event {
    class Pre : MovementEvent()
    class Post : MovementEvent()

    class InputUpdate(
        val input: Input,
        var slowDown: Boolean,
        var slowDownFactor: Float,
    ) : MovementEvent()

    class Sprint : MovementEvent(), ICancellable by Cancellable()

    class ClipAtLedge(
        var clip: Boolean,
    ) : MovementEvent()

    class Jump(var height: Double) : MovementEvent(), ICancellable by Cancellable()
    class SlowDown : Event, ICancellable by Cancellable()
}