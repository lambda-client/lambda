package com.lambda.common.event.events

import com.lambda.common.event.Event

abstract class TickEvent : Event {
    class Pre : TickEvent()
    class Post : TickEvent()
}
