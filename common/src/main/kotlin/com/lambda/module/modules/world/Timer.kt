package com.lambda.module.modules.world

import com.lambda.event.events.ClientEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag

object Timer : Module(
    name = "Timer",
    description = "Modify client tick speed.",
    defaultTags = setOf(ModuleTag.WORLD)
) {
    private val timer by setting("Timer", 50, 0..1000, 5, unit = "ms/tick")

    init {
        listener<ClientEvent.Timer> {
            it.speed = timer / 50.0
        }
    }
}