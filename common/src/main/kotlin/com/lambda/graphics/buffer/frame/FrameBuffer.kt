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
import com.lambda.graphics.RenderMain.projectionMatrix
import com.lambda.graphics.buffer.VertexPipeline
import com.lambda.graphics.buffer.vertex.attributes.VertexAttrib
import com.lambda.graphics.buffer.vertex.attributes.VertexMode
import com.lambda.graphics.gl.Matrices
import com.lambda.graphics.texture.TextureUtils
import org.joml.Matrix4f
import org.lwjgl.opengl.GL12C.GL_CLAMP_TO_EDGE
import org.lwjgl.opengl.GL30C.*
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

    /**
     * Binds this framebuffer, updates its configuration, executes the given rendering block, and restores the previous framebuffer binding.
     *
     * Temporarily replaces the currently bound framebuffer with this instance, ensuring it is properly configured by invoking an update,
     * and then executes the provided block of rendering commands. After the block completes, it restores the prior framebuffer context.
     *
     * @param block the lambda containing rendering operations to execute while this framebuffer is active.
     * @return this framebuffer instance.
     */
    open fun write(block: () -> Unit): FrameBuffer {
        val prev = lastFrameBuffer ?: mc.framebuffer.fbo

        glBindFramebuffer(GL_FRAMEBUFFER, fbo)
        lastFrameBuffer = fbo

        update()

        block()

        lastFrameBuffer = prev
        glBindFramebuffer(GL_FRAMEBUFFER, prev)
        return this
    }

    /**
     * Updates the framebuffer attachments to match the current dimensions.
     *
     * If the current width and height match the previously recorded dimensions, the function
     * simply clears the framebuffer using the configured clear mask. Otherwise, it updates the
     * color texture attachment and, if enabled, the depth texture attachment by reconfiguring
     * texture parameters and reallocating storage to the new dimensions. After resetting the clear
     * color and depth values and clearing the framebuffer, it verifies that the framebuffer is complete,
     * throwing an error if it is not.
     */
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

    /**
     * Binds the framebuffer's color attachment texture to the specified texture slot.
     *
     * This method makes the framebuffer's color texture available for use in subsequent rendering operations.
     * The current instance is returned to facilitate method chaining.
     *
     * @param slot The texture slot index to which the color texture is bound. Defaults to 0.
     * @return The current FrameBuffer instance.
     */
    open fun bindColorTexture(slot: Int = 0): FrameBuffer {
        TextureUtils.bindTexture(colorAttachment, slot)
        return this
    }

    /**
     * Binds the framebuffer's depth texture to the specified texture slot.
     *
     * This method binds the depth texture to the given texture unit, enabling it for subsequent rendering operations.
     * If the framebuffer was created without a depth attachment, an [IllegalStateException] is thrown.
     *
     * @param slot the texture slot to bind the depth texture to (default is 0).
     * @return this [FrameBuffer] instance.
     * @throws IllegalStateException if the framebuffer does not have a depth attachment.
     */
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

        /**
         * Configures an OpenGL texture object with linear filtering and clamp-to-edge wrapping.
         *
         * This function binds the texture identified by [id], sets both its minification and magnification filters to
         * linear filtering, and applies clamp-to-edge wrapping for its S and T coordinates.
         *
         * @param id the identifier of the texture to configure.
         */
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