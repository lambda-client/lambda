package com.lambda.module.modules.player

import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag

object FastPlace : Module(
    name = "FastPlace",
    description = "Place blocks faster.",
    defaultTags = setOf(
        ModuleTag.PLAYER, ModuleTag.WORLD
    )
) {
    init {
        listener<TickEvent.Pre> {
            mc.itemUseCooldown = 0
        }
    }
}
