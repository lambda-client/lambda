package com.lambda.module.modules

import com.lambda.event.events.ClientEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag

object TimerTest : Module(
    name = "TimerTest",
    description = "Test module for timer",
    defaultTags = setOf(ModuleTag.CLIENT)
) {
    private val timer by setting("Timer", 0.5, 0.1..10.0, 0.1)

    init {
        listener<ClientEvent.Timer> {
            it.speed = timer
        }
    }
}