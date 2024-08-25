package com.lambda.graphics.renderer.esp.global

import com.lambda.event.EventFlow.post
import com.lambda.event.events.RenderEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.graphics.buffer.BufferUsage
import com.lambda.graphics.renderer.esp.impl.StaticESPRenderer

object StaticESP : StaticESPRenderer(BufferUsage.DYNAMIC, false) {
    init {
        listener<TickEvent.Post> {
            clear()
            RenderEvent.StaticESP().post()
            upload()
        }
    }
}
