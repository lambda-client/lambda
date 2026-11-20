/*
 * Copyright 2026 Lambda
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

package com.lambda.gui.core

import org.lwjgl.opengl.GL11.GL_BLEND
import org.lwjgl.opengl.GL11.GL_COLOR_WRITEMASK
import org.lwjgl.opengl.GL11.GL_CULL_FACE
import org.lwjgl.opengl.GL11.GL_DEPTH_TEST
import org.lwjgl.opengl.GL11.GL_DEPTH_WRITEMASK
import org.lwjgl.opengl.GL11.GL_SCISSOR_BOX
import org.lwjgl.opengl.GL11.GL_SCISSOR_TEST
import org.lwjgl.opengl.GL11.GL_STENCIL_TEST
import org.lwjgl.opengl.GL11.GL_TEXTURE_2D
import org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D
import org.lwjgl.opengl.GL11.GL_UNPACK_ALIGNMENT
import org.lwjgl.opengl.GL11.GL_UNPACK_LSB_FIRST
import org.lwjgl.opengl.GL11.GL_UNPACK_ROW_LENGTH
import org.lwjgl.opengl.GL11.GL_UNPACK_SKIP_PIXELS
import org.lwjgl.opengl.GL11.GL_UNPACK_SKIP_ROWS
import org.lwjgl.opengl.GL11.GL_UNPACK_SWAP_BYTES
import org.lwjgl.opengl.GL11.GL_VIEWPORT
import org.lwjgl.opengl.GL11.glBindTexture
import org.lwjgl.opengl.GL11.glColorMask
import org.lwjgl.opengl.GL11.glDepthMask
import org.lwjgl.opengl.GL11.glDisable
import org.lwjgl.opengl.GL11.glEnable
import org.lwjgl.opengl.GL11.glGetBoolean
import org.lwjgl.opengl.GL11.glGetBooleanv
import org.lwjgl.opengl.GL11.glGetInteger
import org.lwjgl.opengl.GL11.glGetIntegerv
import org.lwjgl.opengl.GL11.glIsEnabled
import org.lwjgl.opengl.GL11.glPixelStorei
import org.lwjgl.opengl.GL11.glScissor
import org.lwjgl.opengl.GL11.glViewport
import org.lwjgl.opengl.GL12.GL_UNPACK_IMAGE_HEIGHT
import org.lwjgl.opengl.GL12.GL_UNPACK_SKIP_IMAGES
import org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE
import org.lwjgl.opengl.GL13.GL_TEXTURE0
import org.lwjgl.opengl.GL13.glActiveTexture
import org.lwjgl.opengl.GL14.GL_BLEND_DST_ALPHA
import org.lwjgl.opengl.GL14.GL_BLEND_DST_RGB
import org.lwjgl.opengl.GL14.GL_BLEND_SRC_ALPHA
import org.lwjgl.opengl.GL14.GL_BLEND_SRC_RGB
import org.lwjgl.opengl.GL14.glBlendFuncSeparate
import org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER
import org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER_BINDING
import org.lwjgl.opengl.GL15.glBindBuffer
import org.lwjgl.opengl.GL20.GL_BLEND_EQUATION_ALPHA
import org.lwjgl.opengl.GL20.GL_BLEND_EQUATION_RGB
import org.lwjgl.opengl.GL20.GL_CURRENT_PROGRAM
import org.lwjgl.opengl.GL20.glBlendEquationSeparate
import org.lwjgl.opengl.GL20.glUseProgram
import org.lwjgl.opengl.GL21.GL_PIXEL_PACK_BUFFER
import org.lwjgl.opengl.GL21.GL_PIXEL_PACK_BUFFER_BINDING
import org.lwjgl.opengl.GL21.GL_PIXEL_UNPACK_BUFFER
import org.lwjgl.opengl.GL21.GL_PIXEL_UNPACK_BUFFER_BINDING
import org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER
import org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING
import org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER
import org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER_BINDING
import org.lwjgl.opengl.GL30.GL_VERTEX_ARRAY_BINDING
import org.lwjgl.opengl.GL30.glBindFramebuffer
import org.lwjgl.opengl.GL30.glBindVertexArray
import org.lwjgl.opengl.GL33.GL_SAMPLER_BINDING
import org.lwjgl.opengl.GL33.glBindSampler
import org.lwjgl.system.MemoryStack

internal object GlState {
    private const val TEXTURE_UNITS = 12

    private val UNPACK_PARAMETERS =
        intArrayOf(
            GL_UNPACK_SWAP_BYTES, GL_UNPACK_LSB_FIRST, GL_UNPACK_ROW_LENGTH, GL_UNPACK_IMAGE_HEIGHT,
            GL_UNPACK_SKIP_ROWS, GL_UNPACK_SKIP_PIXELS, GL_UNPACK_SKIP_IMAGES, GL_UNPACK_ALIGNMENT
        )
    private val UNPACK_DEFAULTS = intArrayOf(0, 0, 0, 0, 0, 0, 0, 4)
    private val CAPABILITIES = intArrayOf(GL_BLEND, GL_CULL_FACE, GL_DEPTH_TEST, GL_STENCIL_TEST, GL_SCISSOR_TEST)

    private val textures = IntArray(TEXTURE_UNITS)
    private val samplers = IntArray(TEXTURE_UNITS)
    private val unpack = IntArray(UNPACK_PARAMETERS.size)
    private val capabilities = BooleanArray(CAPABILITIES.size)
    private val viewport = IntArray(4)
    private val scissor = IntArray(4)

    inline fun <T> borrow(block: () -> T): T {
        val restore = capture()
        try {
            prepare()
            return block()
        } finally {
            restore()
        }
    }

    fun prepare() {
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0)
        glBindBuffer(GL_PIXEL_PACK_BUFFER, 0)
        UNPACK_PARAMETERS.forEachIndexed { index, parameter -> glPixelStorei(parameter, UNPACK_DEFAULTS[index]) }
    }

    fun capture(): () -> Unit {
        val activeTexture = glGetInteger(GL_ACTIVE_TEXTURE)
        for (unit in 0 until TEXTURE_UNITS) {
            glActiveTexture(GL_TEXTURE0 + unit)
            textures[unit] = glGetInteger(GL_TEXTURE_BINDING_2D)
            samplers[unit] = glGetInteger(GL_SAMPLER_BINDING)
        }
        glActiveTexture(activeTexture)

        val program = glGetInteger(GL_CURRENT_PROGRAM)
        val vertexArray = glGetInteger(GL_VERTEX_ARRAY_BINDING)
        val arrayBuffer = glGetInteger(GL_ARRAY_BUFFER_BINDING)
        val unpackBuffer = glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING)
        val packBuffer = glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING)
        val drawFramebuffer = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)
        val readFramebuffer = glGetInteger(GL_READ_FRAMEBUFFER_BINDING)
        val blendSrcRgb = glGetInteger(GL_BLEND_SRC_RGB)
        val blendDstRgb = glGetInteger(GL_BLEND_DST_RGB)
        val blendSrcAlpha = glGetInteger(GL_BLEND_SRC_ALPHA)
        val blendDstAlpha = glGetInteger(GL_BLEND_DST_ALPHA)
        val blendEquationRgb = glGetInteger(GL_BLEND_EQUATION_RGB)
        val blendEquationAlpha = glGetInteger(GL_BLEND_EQUATION_ALPHA)
        val depthMask = glGetBoolean(GL_DEPTH_WRITEMASK)
        val colorMask =
            MemoryStack.stackPush().use { stack ->
                val mask = stack.malloc(4)
                glGetBooleanv(GL_COLOR_WRITEMASK, mask)
                BooleanArray(4) { mask[it].toInt() != 0 }
            }
        glGetIntegerv(GL_VIEWPORT, viewport)
        glGetIntegerv(GL_SCISSOR_BOX, scissor)
        CAPABILITIES.forEachIndexed { index, capability -> capabilities[index] = glIsEnabled(capability) }
        UNPACK_PARAMETERS.forEachIndexed { index, parameter -> unpack[index] = glGetInteger(parameter) }

        return {
            glUseProgram(program)
            for (unit in 0 until TEXTURE_UNITS) {
                glActiveTexture(GL_TEXTURE0 + unit)
                glBindTexture(GL_TEXTURE_2D, textures[unit])
                glBindSampler(unit, samplers[unit])
            }
            glActiveTexture(activeTexture)
            glBindVertexArray(vertexArray)
            glBindBuffer(GL_ARRAY_BUFFER, arrayBuffer)
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, unpackBuffer)
            glBindBuffer(GL_PIXEL_PACK_BUFFER, packBuffer)
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, drawFramebuffer)
            glBindFramebuffer(GL_READ_FRAMEBUFFER, readFramebuffer)
            glBlendEquationSeparate(blendEquationRgb, blendEquationAlpha)
            glBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha)
            glDepthMask(depthMask)
            glColorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3])
            glViewport(viewport[0], viewport[1], viewport[2], viewport[3])
            glScissor(scissor[0], scissor[1], scissor[2], scissor[3])
            CAPABILITIES.forEachIndexed { index, capability ->
                if (capabilities[index]) glEnable(capability) else glDisable(capability)
            }
            UNPACK_PARAMETERS.forEachIndexed { index, parameter -> glPixelStorei(parameter, unpack[index]) }
        }
    }
}
