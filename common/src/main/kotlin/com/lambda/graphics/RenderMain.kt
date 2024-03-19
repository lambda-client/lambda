package com.lambda.graphics

import com.lambda.Lambda.mc
import com.lambda.event.EventFlow
import com.lambda.event.events.RenderEvent
import com.lambda.graphics.gl.GlStateUtils.setupGL
import com.lambda.graphics.gl.Matrices.translate
import com.lambda.module.modules.client.HUD
import net.minecraft.client.util.math.MatrixStack
import org.joml.Matrix4f

object RenderMain {
    var stack = MatrixStack()

    val projectionMatrix = Matrix4f()
    val modelViewMatrix: Matrix4f get() = stack.peek().positionMatrix

    @JvmStatic
    fun render2D() {
        stack = MatrixStack()

        setupGL {
            rescale(HUD.scale)
            EventFlow.post(RenderEvent.GUI.Scaled(HUD.scale))

            rescale(1.0)
            EventFlow.post(RenderEvent.GUI.Fixed())
        }
    }

    private fun rescale(factor: Double) {
        val width = mc.window.framebufferWidth.toFloat()
        val height = mc.window.framebufferHeight.toFloat()
        val scaledWidth = width / factor
        val scaledHeight = height / factor

        projectionMatrix.setOrtho(0f, scaledWidth.toFloat(), scaledHeight.toFloat(), 0f, -1000f, 1000f)
    }
}