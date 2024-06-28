package com.lambda.event.events

import com.lambda.Lambda.mc
import com.lambda.event.Event
import com.lambda.event.callback.Cancellable
import com.lambda.event.callback.ICancellable
import com.lambda.graphics.renderer.esp.global.BlockESPRenderer
import com.lambda.graphics.renderer.esp.global.EntityESPRenderer
import com.lambda.util.math.Vec2d

abstract class RenderEvent : Event {
    class World : RenderEvent()

    class BlockESP : RenderEvent() {
        val renderer = BlockESPRenderer
    }

    class EntityESP : RenderEvent() {
        val renderer = EntityESPRenderer
    }

    abstract class GUI(val scale: Double) : RenderEvent() {
        class HUD(scaleFactor: Double) : GUI(scaleFactor)

        class Scaled(scaleFactor: Double) : GUI(scaleFactor)
        class Fixed : GUI(1.0)

        val screenSize = Vec2d(mc.window.framebufferWidth, mc.window.framebufferHeight) / scale
    }

    class UpdateTarget : RenderEvent(), ICancellable by Cancellable()
}