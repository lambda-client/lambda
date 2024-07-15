package com.lambda.event.events

import com.lambda.event.Event
import com.lambda.event.callback.Cancellable
import com.lambda.event.callback.ICancellable
import net.minecraft.util.Hand

abstract class EntityEvent : Event {
    class ChangeLookDirection(
        val deltaYaw: Double,
        val deltaPitch: Double,
    ) : EntityEvent(), ICancellable by Cancellable()

    class SwingHand(
        val hand: Hand
    ) : EntityEvent(), ICancellable by Cancellable()
}