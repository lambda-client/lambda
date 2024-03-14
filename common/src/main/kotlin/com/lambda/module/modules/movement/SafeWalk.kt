package com.lambda.module.modules.movement

import com.lambda.event.events.MovementEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag

object SafeWalk : Module(
    name = "SafeWalk",
    description = "Keeps you at the edge",
    defaultTags = setOf(ModuleTag.MOVEMENT)
) {
    init {
        listener<MovementEvent.ClipAtLedge> {
            it.cancel()
        }
    }
}