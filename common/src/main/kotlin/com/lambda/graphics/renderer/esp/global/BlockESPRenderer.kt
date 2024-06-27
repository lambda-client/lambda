package com.lambda.graphics.renderer.esp.global

import com.lambda.event.EventFlow.post
import com.lambda.event.events.RenderEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.graphics.buffer.vao.vertex.BufferUsage
import com.lambda.graphics.renderer.esp.ESPRenderer

object BlockESPRenderer : ESPRenderer(BufferUsage.DYNAMIC) {
    init {
        listener<TickEvent.Post> {
            clear()
            RenderEvent.BlockESP().post()
            upload()
        }
    }
}