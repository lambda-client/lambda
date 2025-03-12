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

    override fun write(block: () -> Unit): ScreenFrameBuffer {
        width = mc.window.framebufferWidth
        height = mc.window.framebufferHeight

        return super.write {
            withBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA, block)
        } as ScreenFrameBuffer
    }

    override fun bindColorTexture(slot: Int) =
        super.bindColorTexture(slot) as ScreenFrameBuffer

    override fun bindDepthTexture(slot: Int) =
        super.bindDepthTexture(slot) as ScreenFrameBuffer
}