package com.lambda.graphics.renderer.immediate

import com.lambda.Lambda.mc
import com.lambda.graphics.RenderMain
import com.lambda.graphics.buffer.vao.VAO
import com.lambda.graphics.buffer.vao.vertex.VertexAttrib
import com.lambda.graphics.buffer.vao.vertex.VertexMode
import com.lambda.graphics.shader.Shader
import com.lambda.graphics.texture.TextureUtils.bindTexture
import com.lambda.util.math.Rect
import com.lambda.util.math.Vec2d

object BlurPostProcessor {
    private val vao = VAO(VertexMode.TRIANGLES, VertexAttrib.Group.BLUR)
    private val shader = Shader("post/blur")

    fun render(rect: Rect, level: Int, alpha: Double) {
        if (level <= 0 || alpha <= 0.1) return
        renderPass(rect, Vec2d.RIGHT, level, alpha)
        renderPass(rect, Vec2d.BOTTOM, level, alpha)
    }

    private fun renderPass(rect: Rect, direction: Vec2d, level: Int, alpha: Double) {
        val x1 = rect.leftTop.x
        val y1 = rect.leftTop.y
        val x2 = rect.rightBottom.x
        val y2 = rect.rightBottom.y

        val screen = RenderMain.screenSize
        val uv1x = x1 / screen.x
        val uv1y = y1 / screen.y
        val uv2x = x2 / screen.x
        val uv2y = y2 / screen.y

        vao.use {
            putQuad(
                vec2(x1, y1).vec2(uv1x, 1.0 - uv1y).end(),
                vec2(x2, y1).vec2(uv2x, 1.0 - uv1y).end(),
                vec2(x2, y2).vec2(uv2x, 1.0 - uv2y).end(),
                vec2(x1, y2).vec2(uv1x, 1.0 - uv2y).end()
            )

            shader.use()
            shader["u_Direction"] = direction / Vec2d(mc.window.framebufferWidth, mc.window.framebufferHeight)
            shader["u_BlurLevel"] = level
            shader["u_Alpha"] = alpha

            bindTexture(mc.framebuffer.colorAttachment)
            upload()
            render()
            clear()
        }
    }
}