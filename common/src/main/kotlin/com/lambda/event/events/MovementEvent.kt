package com.lambda.event.events

import com.lambda.event.Event
import com.lambda.event.callback.Cancellable
import com.lambda.event.callback.ICancellable
import com.lambda.event.callback.IReturnable
import com.lambda.event.callback.Returnable
import net.minecraft.client.input.Input

abstract class MovementEvent : Event {
    class Pre : MovementEvent()
    class Post : MovementEvent()
    class InputUpdate(
        val input: Input,
        val slowDown: Boolean,
        val slowDownFactor: Float,
    ) : MovementEvent(), ICancellable by Cancellable()

    class ClipAtLedge(
        var defaultValue: Boolean,
    ) : MovementEvent(), IReturnable<Boolean> by Returnable(defaultValue)

    class Jump(var height: Double) : MovementEvent(), ICancellable by Cancellable()
    class SlowDown : Event, ICancellable by Cancellable()
}