package com.lambda.graphics

import com.lambda.Lambda.mc
import com.lambda.event.EventFlow.post
import com.lambda.event.events.RenderEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.graphics.animation.Animation.Companion.exp
import com.lambda.graphics.animation.AnimationTicker
import com.lambda.graphics.buffer.FrameBuffer
import com.lambda.graphics.gl.GlStateUtils.setupGL
import com.lambda.graphics.gl.Matrices
import com.lambda.graphics.gl.Matrices.resetMatrices
import com.lambda.graphics.renderer.esp.global.StaticESP
import com.lambda.graphics.renderer.esp.global.DynamicESP
import com.lambda.graphics.shader.Shader
import com.lambda.module.modules.client.ClickGui
import com.lambda.module.modules.client.GuiSettings
import com.lambda.util.math.Vec2d
import com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix
import org.joml.Matrix4f

object RenderMain {
    val projectionMatrix = Matrix4f()
    val modelViewMatrix: Matrix4f get() = Matrices.peek()
    var screenSize = Vec2d.ZERO

    private val hudAnimation0 = with(AnimationTicker()) {
        listener<TickEvent.Pre> {
            tick()
        }

        exp(0.0, 1.0, {
            if (mc.currentScreen == null) ClickGui.closeSpeed else ClickGui.openSpeed
        }) { mc.currentScreen == null }
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
            StaticESP.render()
            DynamicESP.render()
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
