package com.lambda.event.events

import com.lambda.Lambda.mc
import com.lambda.event.Event
import com.lambda.event.callback.Cancellable
import com.lambda.event.callback.ICancellable
import com.lambda.graphics.renderer.esp.DirectionMask
import com.lambda.graphics.renderer.esp.EntityEspRenderer
import com.lambda.util.math.Vec2d
import net.minecraft.entity.Entity
import java.awt.Color

abstract class RenderEvent : Event {
    class World : RenderEvent()

    class EntityESP : RenderEvent() {
        fun build(
            entity: Entity,
            filledColor: Color,
            outlineColor: Color,
            sides: Int = DirectionMask.ALL,
            outlineMode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.OR
        ) {
            buildFilled(entity, filledColor, sides)
            buildOutline(entity, outlineColor, sides, outlineMode)
        }

        fun buildFilled(
            entity: Entity,
            color: Color,
            sides: Int = DirectionMask.ALL
        ) = EntityEspRenderer.buildFilled(entity, color, sides)

        fun buildOutline(
            entity: Entity,
            color: Color,
            sides: Int = DirectionMask.ALL,
            outlineMode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.OR
        ) = EntityEspRenderer.buildOutline(entity, color, sides, outlineMode)
    }

    abstract class GUI(val scale: Double) : RenderEvent() {
        class Scaled(scaleFactor: Double) : GUI(scaleFactor)
        class Fixed : GUI(1.0)

        val screenSize = Vec2d(mc.window.framebufferWidth, mc.window.framebufferHeight) / scale
    }

    class UpdateTarget : RenderEvent(), ICancellable by Cancellable()
}