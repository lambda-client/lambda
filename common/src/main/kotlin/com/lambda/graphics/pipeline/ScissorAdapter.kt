/*
 * Copyright 2024 Lambda
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

package com.lambda.graphics.pipeline

import com.lambda.Lambda.mc
import com.lambda.graphics.RenderMain
import com.lambda.util.math.MathUtils.ceilToInt
import com.lambda.util.math.MathUtils.floorToInt
import com.lambda.util.math.Rect
import com.mojang.blaze3d.systems.RenderSystem.disableScissor
import com.mojang.blaze3d.systems.RenderSystem.enableScissor

object ScissorAdapter {
    private var stack = ArrayDeque<Rect>()

    /**
     * Restricts rendering operations to the specified scissor rectangle.
     *
     * This function clamps the provided rectangle to the boundaries of the current scissor area (if one exists),
     * pushes the resulting rectangle onto an internal stack, and sets the scissor test to that area. It then
     * executes the given lambda block within this constrained context. After the block completes, the function
     * restores the previous scissor state or disables scissor testing if no prior rectangle exists.
     *
     * @param rect The area to which rendering should be confined.
     * @param block A lambda with rendering instructions to execute within the scissor area.
     */
    fun scissor(rect: Rect, block: () -> Unit) {
        val processed = stack.lastOrNull()?.let(rect::clamp) ?: rect

        stack.add(processed)
        scissorRect(processed)

        block()

        stack.removeLast()
        stack.lastOrNull()?.let { scissorRect(it) } ?: disableScissor()
    }

    /**
     * Configures the scissor test region using the provided rectangle.
     *
     * The function scales the rectangle by the current rendering scale factor, computes the screen-space coordinates,
     * and adjusts the y-coordinate relative to the framebuffer height. It then enables the scissor test with the computed
     * integer bounds.
     *
     * @param rect The rectangle defining the area to restrict rendering.
     */
    private fun scissorRect(rect: Rect) {
        val pos1 = rect.leftTop * RenderMain.scaleFactor
        val pos2 = rect.rightBottom * RenderMain.scaleFactor

        val width = pos2.x - pos1.x
        val height = pos2.y - pos1.y

        val y = mc.window.framebufferHeight - pos1.y - height

        enableScissor(
            pos1.x.floorToInt(),
            y.floorToInt(),
            width.ceilToInt(),
            height.ceilToInt()
        )
    }
}