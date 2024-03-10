package com.lambda.event.events

import com.lambda.event.Event

abstract class TickEvent : Event {
    class Pre : TickEvent()
    class Post : TickEvent()
}