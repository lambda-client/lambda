package com.lambda.event.events

import com.lambda.Lambda.mc
import com.lambda.event.Event
import com.lambda.event.callback.Cancellable
import com.lambda.event.callback.ICancellable
import com.lambda.util.math.Vec2d
import net.minecraft.entity.LivingEntity
import net.minecraft.util.math.BlockPos
import java.awt.Color

abstract class RenderEvent : Event {
    class World : RenderEvent()

    abstract class GUI(val scale: Double) : RenderEvent() {
        class Scaled(scaleFactor: Double) : GUI(scaleFactor)
        class Fixed : GUI(1.0)

        val screenSize = Vec2d(mc.window.framebufferWidth, mc.window.framebufferHeight) / scale
    }

    class UpdateTarget : RenderEvent(), ICancellable by Cancellable()
}