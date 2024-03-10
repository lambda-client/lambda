package com.lambda.module.modules

import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag

object BoringModule2 : Module(
    name = "BoringModule2",
    description = "This is a boring module",
    tags = setOf(ModuleTag.MISC, ModuleTag.COMBAT),
) {
    private val superBoring by setting("Super Boring", false)

    init {
        listener<TickEvent.Pre> {
            if (isEnabled) println("I'm ${if (superBoring) "super boring" else "boring"}!")
        }
    }
}