package com.lambda.event.events

import com.lambda.event.Event
import com.lambda.event.callback.Cancellable
import com.lambda.event.callback.ICancellable

abstract class RenderEvent : Event {
    class UpdateTarget : RenderEvent(), ICancellable by Cancellable()
}