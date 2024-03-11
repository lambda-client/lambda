package com.lambda.event.events

import com.lambda.config.Configuration
import com.lambda.event.Event
import com.lambda.module.Module


abstract class ClientEvent : Event {
    data object Shutdown : ClientEvent()
    data object Startup : ClientEvent()
    data class ConfigLoaded(val configuration: Configuration) : ClientEvent()
    data class ConfigSaved(val configuration: Configuration) : ClientEvent()
}