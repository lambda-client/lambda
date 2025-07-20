/*
 * Copyright 2025 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.graphics.renderer

import com.lambda.Lambda.mc
import com.lambda.graphics.RenderMain
import com.lambda.util.math.MathUtils.ceilToInt
import com.lambda.util.math.MathUtils.floorToInt
import com.lambda.util.math.Rect
import com.mojang.blaze3d.systems.RenderSystem.disableScissor
import com.mojang.blaze3d.systems.RenderSystem.enableScissor

object ScissorAdapter {
    private var stack = ArrayDeque<Rect>()

    fun scissor(rect: Rect, block: () -> Unit) {
        val processed = stack.lastOrNull()?.let(rect::clamp) ?: rect

        stack.add(processed)
        scissorRect(processed)

        block()

        stack.removeLast()
        stack.lastOrNull()?.let { scissorRect(it) } ?: disableScissor()
    }

    private fun scissorRect(rect: Rect) {
        val pos1 = rect.leftTop * RenderMain.scaleFactor
        val pos2 = rect.rightBottom * RenderMain.scaleFactor

        val width = (pos2.x - pos1.x).coerceAtLeast(0.0)
        val height = (pos2.y - pos1.y).coerceAtLeast(0.0)

        val y = mc.window.framebufferHeight - pos1.y - height

        enableScissor(
            pos1.x.floorToInt(),
            y.floorToInt(),
            width.ceilToInt(),
            height.ceilToInt()
        )
    }
}