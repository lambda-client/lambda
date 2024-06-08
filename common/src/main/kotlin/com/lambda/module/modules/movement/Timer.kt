package com.lambda.module.modules.movement

import com.lambda.event.events.ClientEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag

object Timer : Module(
    name = "Timer",
    description = "Modify client tick speed.",
    defaultTags = setOf(ModuleTag.MOVEMENT, ModuleTag.WORLD)
) {
    private val timer by setting("Timer", 1.0, 0.0..10.0, 0.01)

    init {
        listener<ClientEvent.Timer> {
            it.speed = timer.coerceAtLeast(0.05)
        }
    }
}