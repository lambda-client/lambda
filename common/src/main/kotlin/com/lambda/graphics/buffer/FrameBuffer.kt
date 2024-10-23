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

package com.lambda.graphics.buffer

import com.lambda.Lambda.mc
import com.lambda.graphics.RenderMain
import com.lambda.graphics.buffer.vao.VAO
import com.lambda.graphics.buffer.vao.vertex.VertexAttrib
import com.lambda.graphics.buffer.vao.vertex.VertexMode
import com.lambda.graphics.gl.GlStateUtils.withBlendFunc
import com.lambda.graphics.shader.Shader
import com.lambda.graphics.texture.TextureUtils.bindTexture
import com.lambda.graphics.texture.TextureUtils.setupTexture
import com.lambda.util.math.Vec2d
import org.lwjgl.opengl.GL30.*
import java.nio.IntBuffer

class FrameBuffer(private val depth: Boolean = false) {
    private val fbo = glGenFramebuffers()

    private val colorAttachment = glGenTextures()
    private val depthAttachment by lazy(::glGenTextures)

    private val clearMask = if (!depth) GL_COLOR_BUFFER_BIT
    else GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT

    private var width = 0
    private var height = 0

    fun write(block: () -> Unit): FrameBuffer {
        val prev = lastFrameBuffer ?: mc.framebuffer.fbo

        glBindFramebuffer(GL_FRAMEBUFFER, fbo)
        lastFrameBuffer = fbo

        update()
        withBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA, block)

        lastFrameBuffer = prev
        glBindFramebuffer(GL_FRAMEBUFFER, prev)
        return this
    }

    fun read(
        shader: Shader,
        pos1: Vec2d = Vec2d.ZERO,
        pos2: Vec2d = RenderMain.screenSize,
        shaderBlock: (Shader) -> Unit = {}
    ): FrameBuffer {
        bindColorTexture()

        shader.use()
        shaderBlock(shader)

        vao.use {
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
            vao.upload()
            vao.render()
            vao.clear()
        }

        return this
    }

    fun bindColorTexture(slot: Int = 0): FrameBuffer {
        bindTexture(colorAttachment, slot)
        return this
    }

    fun bindDepthTexture(slot: Int = 0): FrameBuffer {
        check(depth) {
            "Cannot bind depth texture of a non-depth framebuffer"
        }

        bindTexture(depthAttachment, slot)
        return this
    }

    private fun update() {
        val widthIn = mc.window.framebufferWidth
        val heightIn = mc.window.framebufferHeight

        if (width != widthIn || height != heightIn) {
            width = widthIn
            height = heightIn

            setupBufferTexture(colorAttachment)
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, null as IntBuffer?)
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, colorAttachment, 0)

            if (depth) {
                setupBufferTexture(depthAttachment)
                glTexImage2D(
                    GL_TEXTURE_2D,          // Target
                    0,                      // LOD Level
                    GL_DEPTH_COMPONENT32F,  // Internal Format
                    width,                  // Width
                    height,                 // Height
                    0,                      // Border (must be zero)
                    GL_DEPTH_COMPONENT,     // Format
                    GL_FLOAT,               // Type
                    null as IntBuffer?      // Pointer to data
                )
                glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depthAttachment, 0)
            }

            glClearColor(0f, 0f, 0f, 0f)
            glClearDepth(1.0)
        }

        glClear(clearMask)
    }

    private fun setupBufferTexture(id: Int) {
        bindTexture(id)
        setupTexture(GL_NEAREST, GL_NEAREST)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
    }

    companion object {
        private val vao = VAO(VertexMode.TRIANGLES, VertexAttrib.Group.POS_UV)
        private var lastFrameBuffer: Int? = null
    }
}
