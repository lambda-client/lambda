package com.lambda.event.events

import com.lambda.event.Event


abstract class ClientEvent : Event {
    class Shutdown : ClientEvent()
    class Startup : ClientEvent()
    class Timer(var speed: Double) : Event
}