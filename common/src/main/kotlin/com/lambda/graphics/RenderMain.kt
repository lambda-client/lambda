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

package com.lambda.graphics

import com.lambda.Lambda.mc
import com.lambda.event.EventFlow.post
import com.lambda.event.events.RenderEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.graphics.gl.GlStateUtils.setupGL
import com.lambda.graphics.gl.Matrices
import com.lambda.graphics.gl.Matrices.resetMatrices
import com.lambda.graphics.renderer.esp.global.DynamicESP
import com.lambda.graphics.renderer.esp.global.StaticESP
import com.lambda.module.modules.client.GuiSettings
import com.lambda.util.math.Vec2d
import org.joml.Matrix4f

object RenderMain {
    val projectionMatrix = Matrix4f()
    val modelViewMatrix get() = Matrices.peek()
    val projModel get() = Matrix4f(projectionMatrix).mul(modelViewMatrix)

    var screenSize = Vec2d.ZERO
    var scaleFactor = 1.0

    @JvmStatic
    fun render2D() {
        resetMatrices(Matrix4f().translate(0f, 0f, -3000f))

        setupGL {
            rescale(1.0)
            RenderEvent.GUI.Fixed().post()

            rescale(GuiSettings.scale)
            RenderEvent.GUI.HUD(GuiSettings.scale).post()
            RenderEvent.GUI.Scaled(GuiSettings.scale).post()
        }
    }

    @JvmStatic
    fun render3D(positionMatrix: Matrix4f, projectionMatrix: Matrix4f) {
        resetMatrices(positionMatrix)
        projectionMatrix.set(projectionMatrix)

        setupGL {
            RenderEvent.World().post()
            StaticESP.render()
            DynamicESP.render()
        }
    }

    init {
        listen<TickEvent.Post> {
            StaticESP.clear()
            RenderEvent.StaticESP().post()
            StaticESP.upload()

            DynamicESP.clear()
            RenderEvent.DynamicESP().post()
            DynamicESP.upload()
        }
    }

    private fun rescale(factor: Double) {
        val width = mc.window.framebufferWidth.toFloat()
        val height = mc.window.framebufferHeight.toFloat()

        val scaledWidth = width / factor
        val scaledHeight = height / factor

        screenSize = Vec2d(scaledWidth, scaledHeight)
        scaleFactor = factor

        projectionMatrix.setOrtho(0f, scaledWidth.toFloat(), scaledHeight.toFloat(), 0f, 1000f, 21000f)
    }
}
