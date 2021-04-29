package com.lambda.client.event.events

import com.lambda.client.event.Event

abstract class ConnectionEvent : Event {
    class Connect : ConnectionEvent()
    class Disconnect : ConnectionEvent()
}