package com.lambda.graphics.gl

import com.lambda.Lambda.mc
import com.lambda.module.modules.client.HUD
import com.lambda.util.math.MathUtils.ceilToInt
import com.lambda.util.math.MathUtils.floorToInt
import com.lambda.util.math.Rect
import com.lambda.util.math.Vec2d
import com.mojang.blaze3d.systems.RenderSystem.disableScissor
import com.mojang.blaze3d.systems.RenderSystem.enableScissor
import kotlin.math.max
import kotlin.math.min

object Scissor {
    private var stack = ArrayDeque<Entry>()

    fun scissor(rect: Rect, block: () -> Unit) = scissor(rect.leftTop, rect.rightBottom, block)

    fun scissor(pos1: Vec2d, pos2: Vec2d, block: () -> Unit) {
        stack.lastOrNull()?.let {
            registerScissor(
                // clamp corners so children scissor box can't overlap parent
                Vec2d(max(pos1.x, it.pos1.x), max(pos1.y, it.pos1.y)),
                Vec2d(min(pos2.x, it.pos2.x), min(pos2.y, it.pos2.y)),
                block
            )
        } ?: registerScissor(pos1, pos2, block)
    }

    private fun registerScissor(pos1: Vec2d, pos2: Vec2d, block: () -> Unit) {
        scissor(Entry(pos1, pos2))

        block()

        stack.removeLast()
        stack.lastOrNull().apply(::scissor)
    }

    private fun scissor(entry: Entry?) {
        if (entry == null) {
            disableScissor()
            return
        }

        stack.add(entry)

        val pos1 = entry.pos1 * HUD.scale
        val pos2 = entry.pos2 * HUD.scale

        val width = max(pos2.x - pos1.x, 0.0)
        val height = max(pos2.y - pos1.y, 0.0)

        val y = mc.window.framebufferHeight - pos1.y - height

        enableScissor(
            pos1.x.floorToInt(),
            y.floorToInt(),
            width.ceilToInt(),
            height.ceilToInt()
        )
    }

    private data class Entry(val pos1: Vec2d, val pos2: Vec2d)
}