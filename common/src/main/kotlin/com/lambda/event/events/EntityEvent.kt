package com.lambda.event.events

import com.lambda.event.Event
import com.lambda.event.callback.Cancellable
import com.lambda.event.callback.ICancellable

abstract class EntityEvent : Event {
    class ChangeLookDirection(
        val deltaYaw: Double,
        val deltaPitch: Double,
    ) : EntityEvent(), ICancellable by Cancellable()
}