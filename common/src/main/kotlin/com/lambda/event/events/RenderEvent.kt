package com.lambda.event.events

import com.lambda.event.Event
import com.lambda.event.callback.Cancellable
import com.lambda.event.callback.ICancellable

abstract class RenderEvent : Event {
    class World : RenderEvent()

    abstract class GUI(val scaleFactor: Double) : RenderEvent() {
        class Scaled(scaleFactor: Double) : GUI(scaleFactor)
        class Fixed : GUI(1.0)
    }
    class UpdateTarget : RenderEvent(), ICancellable by Cancellable()
}