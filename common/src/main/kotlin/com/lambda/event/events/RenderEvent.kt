package com.lambda.event.events

import com.lambda.event.Event

abstract class RenderEvent : Event {
    class World : RenderEvent()

    abstract class GUI(val scaleFactor: Double) : RenderEvent() {
        class Scaled(scaleFactor: Double) : GUI(scaleFactor)
        class Fixed : GUI(1.0)
    }
}