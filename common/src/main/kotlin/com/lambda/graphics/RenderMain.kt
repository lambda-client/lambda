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

package com.lambda.graphics

import com.lambda.Lambda.mc
import com.lambda.event.EventFlow.post
import com.lambda.event.events.RenderEvent
import com.lambda.graphics.buffer.FrameBuffer
import com.lambda.graphics.gl.GlStateUtils.setupGL
import com.lambda.graphics.gl.Matrices
import com.lambda.graphics.gl.Matrices.resetMatrices
import com.lambda.graphics.pipeline.UIPipeline
import com.lambda.graphics.shader.Shader
import com.lambda.module.modules.client.GuiSettings
import com.lambda.util.math.Vec2d
import com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix
import org.joml.Matrix4f

object RenderMain {
    private val projectionMatrix = Matrix4f()
    private val modelViewMatrix get() = Matrices.peek()
    val projModel get() = Matrix4f(projectionMatrix).mul(modelViewMatrix)

    var screenSize = Vec2d.ZERO

    @JvmStatic
    fun render2D() {
        resetMatrices(Matrix4f().translate(0f, 0f, -3000f))

        setupGL {
            UIPipeline.reset()

            rescale(1.0)
            RenderEvent.GUI.Fixed().post()

            rescale(GuiSettings.scale)
            RenderEvent.GUI.HUD(GuiSettings.scale).post()
            RenderEvent.GUI.Scaled(GuiSettings.scale).post()

            UIPipeline.render()
        }
    }

    @JvmStatic
    fun render3D(matrix: Matrix4f) {
        resetMatrices(matrix)
        projectionMatrix.set(getProjectionMatrix())

        setupGL {
            RenderEvent.World().post()
        }
    }

    private fun rescale(factor: Double) {
        val width = mc.window.framebufferWidth.toFloat()
        val height = mc.window.framebufferHeight.toFloat()

        val scaledWidth = width / factor
        val scaledHeight = height / factor

        screenSize = Vec2d(scaledWidth, scaledHeight)
        projectionMatrix.setOrtho(0f, scaledWidth.toFloat(), scaledHeight.toFloat(), 0f, 1000f, 21000f)
    }
}
