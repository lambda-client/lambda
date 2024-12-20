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
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.graphics.animation.Animation.Companion.exp
import com.lambda.graphics.animation.AnimationTicker
import com.lambda.graphics.buffer.FrameBuffer
import com.lambda.graphics.gl.GlStateUtils.setupGL
import com.lambda.graphics.gl.Matrices
import com.lambda.graphics.gl.Matrices.resetMatrices
import com.lambda.graphics.shader.Shader
import com.lambda.gui.impl.hudgui.LambdaHudGui
import com.lambda.module.modules.client.ClickGui
import com.lambda.module.modules.client.GuiSettings
import com.lambda.util.math.Vec2d
import com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix
import org.joml.Matrix4f

object RenderMain {
    val projectionMatrix = Matrix4f()
    val modelViewMatrix: Matrix4f get() = Matrices.peek()
    var screenSize = Vec2d.ZERO

    private val showHud get() = mc.currentScreen == null || LambdaHudGui.isOpen

    private val hudAnimation0 = with(AnimationTicker()) {
        listen<TickEvent.Pre> {
            tick()
        }

        exp(0.0, 1.0, {
            if (showHud) ClickGui.closeSpeed else ClickGui.openSpeed
        }) { showHud }
    }

    private val frameBuffer = FrameBuffer()
    private val shader = Shader("post/cgui_animation", "renderer/pos_tex")
    private val hudAnimation by hudAnimation0

    @JvmStatic
    fun render2D() {
        resetMatrices(Matrix4f().translate(0f, 0f, -3000f))

        setupGL {
            rescale(1.0)
            RenderEvent.GUI.Fixed().post()

            rescale(GuiSettings.scale)
            drawHUD()
            RenderEvent.GUI.Scaled(GuiSettings.scale).post()
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

    private fun drawHUD() {
        if (hudAnimation < 0.001) return

        if (hudAnimation > 0.999) {
            RenderEvent.GUI.HUD(GuiSettings.scale).post()
            return
        }

        frameBuffer.write {
            RenderEvent.GUI.HUD(GuiSettings.scale).post()
        }.read(shader) {
            it["u_Progress"] = hudAnimation
        }
    }
}
