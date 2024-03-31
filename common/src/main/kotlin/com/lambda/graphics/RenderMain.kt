package com.lambda.graphics

import com.lambda.Lambda.mc
import com.lambda.event.EventFlow
import com.lambda.event.EventFlow.post
import com.lambda.event.events.RenderEvent
import com.lambda.graphics.gl.GlStateUtils.setupGL
import com.lambda.graphics.gl.Matrices
import com.lambda.graphics.gl.Matrices.resetMatrix
import com.lambda.graphics.gl.Matrices.translate
import com.lambda.module.modules.client.HUD
import com.lambda.util.math.Vec2d
import net.minecraft.client.util.math.MatrixStack
import org.joml.Matrix4f

object RenderMain {
    val projectionMatrix = Matrix4f()
    val modelViewMatrix: Matrix4f get() = Matrices.stack.peek().positionMatrix

    var screenSize = Vec2d.ONE

    @JvmStatic
    fun render2D() {
        resetMatrix()
        translate(0.0, 0.0, -3000.0)

        setupGL {
            rescale(HUD.scale)
            RenderEvent.GUI.Scaled(HUD.scale).post()

            rescale(1.0)
            RenderEvent.GUI.Fixed().post()
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