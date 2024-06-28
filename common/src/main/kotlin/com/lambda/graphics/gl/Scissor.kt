package com.lambda.graphics.gl

import com.lambda.Lambda.mc
import com.lambda.module.modules.client.GuiSettings
import com.lambda.util.math.MathUtils.ceilToInt
import com.lambda.util.math.MathUtils.floorToInt
import com.lambda.util.math.Rect
import com.mojang.blaze3d.systems.RenderSystem
import kotlin.math.max

object Scissor {
    private var stack = ArrayDeque<Rect>()

    fun scissor(rect: Rect, block: () -> Unit) {
        // clamp corners so children scissor boxes can't overlap parent
        val processed = stack.lastOrNull()?.let(rect::clamp) ?: rect
        registerScissor(processed, block)
    }

    private fun registerScissor(rect: Rect, block: () -> Unit) {
        stack.add(rect)

        scissor(rect)
        block()

        stack.removeLast()
        scissor(stack.lastOrNull())
    }

    private fun scissor(entry: Rect?) {
        if (entry == null) {
            RenderSystem.disableScissor()
            return
        }

        val pos1 = entry.leftTop * GuiSettings.scale
        val pos2 = entry.rightBottom * GuiSettings.scale

        val width = max(pos2.x - pos1.x, 1.0)
        val height = max(pos2.y - pos1.y, 1.0)

        val y = mc.window.framebufferHeight - pos1.y - height

        RenderSystem.enableScissor(
            pos1.x.floorToInt(),
            y.floorToInt(),
            width.ceilToInt(),
            height.ceilToInt()
        )
    }
}
