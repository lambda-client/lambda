package com.lambda.event.events

import com.lambda.event.Event
import com.lambda.event.cancellable.Cancellable
import com.lambda.event.cancellable.ICancellable

abstract class MovementEvent : Event {
    class ClipAtLedge : MovementEvent(), ICancellable by Cancellable()
}