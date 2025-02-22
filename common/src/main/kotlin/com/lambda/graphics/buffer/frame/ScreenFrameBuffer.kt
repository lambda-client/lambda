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

package com.lambda.graphics.buffer.frame

import com.lambda.Lambda.mc
import com.lambda.graphics.RenderMain
import com.lambda.graphics.gl.GlStateUtils.withBlendFunc
import com.lambda.graphics.shader.Shader
import com.lambda.util.math.Vec2d
import org.lwjgl.opengl.GL11C.*

class ScreenFrameBuffer(depth: Boolean = false) : FrameBuffer(depth = depth) {
    /**
     * Renders the frame buffer content onto a quad using the specified shader.
     *
     * This function binds the frame buffer's color texture and activates the provided shader,
     * allowing for further customization via the optional [shaderBlock]. It then computes normalized
     * UV coordinates based on the given screen positions ([pos1] and [pos2]) relative to the current
     * screen dimensions, and defines a quadrilateral for rendering. Finally, it applies a blending mode,
     * uploads, renders, and clears the internal pipeline.
     *
     * @param shader The shader used for rendering the frame buffer content.
     * @param pos1 The starting screen position (in pixels) for the rendering region. Defaults to [Vec2d.ZERO].
     * @param pos2 The ending screen position (in pixels) for the rendering region. Defaults to [RenderMain.screenSize].
     * @param shaderBlock Optional lambda for additional shader configuration.
     * @return The current instance of [ScreenFrameBuffer] to support method chaining.
     */
    fun read(
        shader: Shader,
        pos1: Vec2d = Vec2d.ZERO,
        pos2: Vec2d = RenderMain.screenSize,
        shaderBlock: (Shader) -> Unit = {}
    ): ScreenFrameBuffer {
        bindColorTexture()

        shader.use()
        shaderBlock(shader)

        pipeline.use {
            grow(4)

            val uv1 = pos1 / RenderMain.screenSize
            val uv2 = pos2 / RenderMain.screenSize

            putQuad(
                vec2(pos1.x, pos1.y).vec2(uv1.x, 1.0 - uv1.y).end(),
                vec2(pos1.x, pos2.y).vec2(uv1.x, 1.0 - uv2.y).end(),
                vec2(pos2.x, pos2.y).vec2(uv2.x, 1.0 - uv2.y).end(),
                vec2(pos2.x, pos1.y).vec2(uv2.x, 1.0 - uv1.y).end()
            )
        }

        withBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA) {
            pipeline.upload()
            pipeline.render()
            pipeline.clear()
        }

        return this
    }

    /**
     * Updates the frame buffer's dimensions to match the current window size and executes rendering instructions with a specific blend configuration.
     *
     * This method sets the frame buffer's width and height based on the current Minecraft window dimensions, then calls the superclass 
     * write method while wrapping the provided rendering block within a blend function using source alpha and one minus source alpha factors.
     *
     * @param block the lambda containing rendering instructions to be executed with the configured blending.
     * @return the current instance of ScreenFrameBuffer.
     */
    override fun write(block: () -> Unit): ScreenFrameBuffer {
        width = mc.window.framebufferWidth
        height = mc.window.framebufferHeight

        return super.write {
            withBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA, block)
        } as ScreenFrameBuffer
    }

    /**
         * Binds the color texture to the specified slot and returns the current instance as a ScreenFrameBuffer.
         *
         * This method delegates to the superclass implementation and casts its result to ensure type consistency.
         *
         * @param slot The texture binding slot.
         */
        override fun bindColorTexture(slot: Int) =
        super.bindColorTexture(slot) as ScreenFrameBuffer

    /**
         * Binds the depth texture to the specified slot by invoking the superclass method and casting the result
         * to a ScreenFrameBuffer.
         *
         * @param slot the texture slot to which the depth texture is bound.
         * @return the current ScreenFrameBuffer instance.
         */
        override fun bindDepthTexture(slot: Int) =
        super.bindDepthTexture(slot) as ScreenFrameBuffer
}