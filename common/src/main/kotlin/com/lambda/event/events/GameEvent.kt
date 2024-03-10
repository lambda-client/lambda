package com.lambda.event.events

import com.lambda.event.Event


abstract class GameEvent : Event {
    data object Shutdown : GameEvent()
    data object Startup : GameEvent()
}