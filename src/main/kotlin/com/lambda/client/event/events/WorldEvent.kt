package com.lambda.client.event.events

import com.lambda.client.event.Event
import com.lambda.client.event.ProfilerEvent
import com.lambda.client.mixin.extension.renderPosX
import com.lambda.client.mixin.extension.renderPosY
import com.lambda.client.mixin.extension.renderPosZ
import com.lambda.client.util.Wrapper
import com.lambda.client.util.graphics.LambdaTessellator
import net.minecraft.entity.Entity
import net.minecraft.world.Explosion

open class WorldEvent {
    class Join(val entity: Entity) : Event

    class Leave(val entity: Entity) : Event

    class EntityCreate(val entity: Entity) : Event

    class EntityDestroy(val entity: Entity) : Event

    class PreExplosion(val explosion: Explosion) : Event

    class PostExplosion(val explosion: Explosion) : Event

    class RenderTickEvent : Event, ProfilerEvent {
        override val profilerName: String = "kbRender3D"

        init {
            LambdaTessellator.buffer.setTranslation(
                -Wrapper.minecraft.renderManager.renderPosX,
                -Wrapper.minecraft.renderManager.renderPosY,
                -Wrapper.minecraft.renderManager.renderPosZ
            )
        }
    }
}