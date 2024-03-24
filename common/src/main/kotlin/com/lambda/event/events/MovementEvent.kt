package com.lambda.event.events

import com.lambda.event.Event
import com.lambda.event.cancellable.Cancellable
import com.lambda.event.cancellable.ICancellable
import net.minecraft.client.input.Input

abstract class MovementEvent : Event {
    class Pre : MovementEvent()
    class Post : MovementEvent()
    class InputUpdate(
        val input: Input,
        val slowDown: Boolean,
        val slowDownFactor: Float
    ) : MovementEvent(), ICancellable by Cancellable()
    class ClipAtLedge : MovementEvent(), ICancellable by Cancellable()
    class Jump(var height: Double) : MovementEvent(), ICancellable by Cancellable()
    class SlowDown : Event, ICancellable by Cancellable()
}