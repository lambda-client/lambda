package com.lambda.event.events

import com.lambda.event.Event


abstract class ClientEvent : Event {
    data object Shutdown : ClientEvent()
    data object Startup : ClientEvent()
}