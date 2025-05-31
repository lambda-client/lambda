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

package com.lambda.graphics.buffer.frame

import com.lambda.graphics.buffer.vertex.attributes.VertexAttrib
import com.lambda.graphics.buffer.vertex.attributes.VertexMode
import com.lambda.graphics.pipeline.VertexPipeline
import com.lambda.graphics.texture.TextureUtils
import org.lwjgl.opengl.GL12C.GL_CLAMP_TO_EDGE
import org.lwjgl.opengl.GL30C.GL_COLOR_ATTACHMENT0
import org.lwjgl.opengl.GL30C.GL_COLOR_BUFFER_BIT
import org.lwjgl.opengl.GL30C.GL_DEPTH_ATTACHMENT
import org.lwjgl.opengl.GL30C.GL_DEPTH_BUFFER_BIT
import org.lwjgl.opengl.GL30C.GL_DEPTH_COMPONENT
import org.lwjgl.opengl.GL30C.GL_DEPTH_COMPONENT32F
import org.lwjgl.opengl.GL30C.GL_FLOAT
import org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER
import org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER_COMPLETE
import org.lwjgl.opengl.GL30C.GL_LINEAR
import org.lwjgl.opengl.GL30C.GL_RGBA
import org.lwjgl.opengl.GL30C.GL_TEXTURE_2D
import org.lwjgl.opengl.GL30C.GL_TEXTURE_MAG_FILTER
import org.lwjgl.opengl.GL30C.GL_TEXTURE_MIN_FILTER
import org.lwjgl.opengl.GL30C.GL_TEXTURE_WRAP_S
import org.lwjgl.opengl.GL30C.GL_TEXTURE_WRAP_T
import org.lwjgl.opengl.GL30C.GL_UNSIGNED_BYTE
import org.lwjgl.opengl.GL30C.glBindFramebuffer
import org.lwjgl.opengl.GL30C.glCheckFramebufferStatus
import org.lwjgl.opengl.GL30C.glClear
import org.lwjgl.opengl.GL30C.glClearColor
import org.lwjgl.opengl.GL30C.glClearDepth
import org.lwjgl.opengl.GL30C.glFramebufferTexture2D
import org.lwjgl.opengl.GL30C.glGenFramebuffers
import org.lwjgl.opengl.GL30C.glGenTextures
import org.lwjgl.opengl.GL30C.glTexImage2D
import org.lwjgl.opengl.GL30C.glTexParameteri
import java.nio.IntBuffer

open class FrameBuffer(
    protected var width: Int = 1,
    protected var height: Int = 1,
    private val depth: Boolean = false
) {
    private val fbo = glGenFramebuffers()

    private val colorAttachment = glGenTextures()
    private val depthAttachment by lazy(::glGenTextures)

    private val clearMask = if (!depth) GL_COLOR_BUFFER_BIT
    else GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT

    private var lastWidth = -1
    private var lastHeight = -1

    @Deprecated(message = "Fix this code")
    open fun write(block: () -> Unit): FrameBuffer {
        //val prev = lastFrameBuffer ?: mc.framebuffer.fbo

        glBindFramebuffer(GL_FRAMEBUFFER, fbo)
        //lastFrameBuffer = fbo

        update()

        block()

        //lastFrameBuffer = prev
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
        return this
    }

    private fun update() {
        if (width == lastWidth && height == lastWidth) {
            glClear(clearMask)
            return
        }

        lastWidth = width
        lastHeight = height

        setupBufferTexture(colorAttachment)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, null as IntBuffer?)
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, colorAttachment, 0)

        if (depth) {
            setupBufferTexture(depthAttachment)
            glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT32F, width, height, 0, GL_DEPTH_COMPONENT, GL_FLOAT, null as IntBuffer?)
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depthAttachment, 0)
        }

        glClearColor(0f, 0f, 0f, 0f)
        glClearDepth(1.0)

        glClear(clearMask)

        val fboStatus = glCheckFramebufferStatus(GL_FRAMEBUFFER)

        check(fboStatus == GL_FRAMEBUFFER_COMPLETE) {
            "Framebuffer not complete: $fboStatus"
        }
    }

    open fun bindColorTexture(slot: Int = 0): FrameBuffer {
        TextureUtils.bindTexture(colorAttachment, slot)
        return this
    }

    open fun bindDepthTexture(slot: Int = 0): FrameBuffer {
        check(depth) {
            "Cannot bind depth texture of a non-depth framebuffer"
        }

        TextureUtils.bindTexture(depthAttachment, slot)
        return this
    }

    companion object {
        val pipeline = VertexPipeline(VertexMode.TRIANGLES, VertexAttrib.Group.POS_UV)
        private var lastFrameBuffer: Int? = null

        private fun setupBufferTexture(id: Int) {
            TextureUtils.bindTexture(id)
            TextureUtils.setupTexture(GL_LINEAR, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        }
    }
}
