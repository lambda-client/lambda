package com.lambda.module.modules

import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.module.Module
import com.lambda.util.world.EntityUtils.getClosestEntity
import net.minecraft.entity.Entity

object EntityTest : Module(
    name = "EntityTest",
    description = "Test entity",
    defaultTags = setOf()
) {
    init {
        listener<TickEvent.Pre> {
            repeat(10000) {
                getClosestEntity<Entity>(player.eyePos, 7.0)
            }
        }
    }
}
