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

package com.lambda.graphics.renderer.gui

import com.lambda.Lambda.mc
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.graphics.buffer.frame.FrameBuffer
import com.lambda.graphics.buffer.vertex.attributes.VertexAttrib
import com.lambda.graphics.buffer.vertex.attributes.VertexMode
import com.lambda.graphics.gl.GlStateUtils
import com.lambda.graphics.pipeline.VertexPipeline
import com.lambda.graphics.shader.Shader.Companion.shader
import com.lambda.graphics.texture.TextureUtils.bindTexture
import com.lambda.util.math.Rect
import com.lambda.util.math.Vec2d
import org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER
import org.lwjgl.opengl.GL30C.glBindFramebuffer

// ToDo: Improve blur
// Round Corners
// Boost brightness
// BlurRect layout impl
object BlurRenderer {
    private val fbo1 = FrameBuffer()
    private val fbo2 = FrameBuffer()
    private val base get() = mc.framebuffer

    private val hShader = shader("post/gaussian_h")
    private val vShader = shader("post/gaussian_v")

    fun blur(rect: Rect, iterations: Int) {
        if (iterations <= 0) return

        val i = iterations.toDouble()
        val texelSize = Vec2d(1.0 / base.viewportWidth, 1.0 / base.viewportHeight)
        val (pos1, pos2) = rect.leftTop to rect.rightBottom

        hShader.use()
        hShader["u_TexelSize"] = texelSize
        hShader["u_Position1"] = pos1
        hShader["u_Position2"] = pos2

        vShader.use()
        vShader["u_TexelSize"] = texelSize
        vShader["u_Position1"] = pos1
        vShader["u_Position2"] = pos2

        GlStateUtils.blend(false)
        render(null, fbo1, false, i - 1.0, i)

        repeat(iterations - 1) {
            render(fbo1, fbo2, true , i - it, i - it)
            render(fbo2, fbo1, false, i-it-1, i - it)
        }

        render(fbo1, null, true)
        GlStateUtils.blend(true)
    }

    private fun render(
        color: FrameBuffer?,
        frameBuffer: FrameBuffer?,
        vertical: Boolean,
        extendX: Double = 0.0,
        extendY: Double = 0.0
    ) {
        color?.bindColorTexture() ?: bindTexture(base.colorAttachment)

        glBindFramebuffer(GL_FRAMEBUFFER, frameBuffer?.fbo ?: base.fbo)
        frameBuffer?.updateScreenSized()

        (if (vertical) vShader else hShader).let { shader ->
            shader.use()
            shader["u_Extend"] = Vec2d(extendX, extendY)
            // if (vertical) shader["u_IsFinal"] = frameBuffer == null
        }

        VertexPipeline.renderStaticRect()
    }
}
