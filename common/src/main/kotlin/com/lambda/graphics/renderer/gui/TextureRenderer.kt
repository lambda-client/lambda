package com.lambda.graphics.renderer.gui

import com.lambda.graphics.RenderMain
import com.lambda.graphics.buffer.vao.VAO
import com.lambda.graphics.buffer.vao.vertex.VertexAttrib
import com.lambda.graphics.buffer.vao.vertex.VertexMode
import com.lambda.graphics.shader.Shader
import com.lambda.graphics.texture.Texture
import com.lambda.module.modules.client.GuiSettings
import com.lambda.util.math.Rect
import com.lambda.util.math.Vec2d
import org.lwjgl.glfw.GLFW.glfwGetTime

object TextureRenderer {
    private val vao = VAO(VertexMode.TRIANGLES, VertexAttrib.Group.POS_UV)
    private val shader = Shader("renderer/pos_tex")
    private val shaderColored = Shader("renderer/pos_tex_shady")

    fun drawTexture(texture: Texture, rect: Rect) {
        texture.bind()
        shader.use()

        drawInternal(rect)
    }

    fun drawTextureShaded(texture: Texture, rect: Rect, shadeWidthScale: Double = 1.0) {
        texture.bind()
        shaderColored.use()

        shaderColored["u_Time"] = glfwGetTime() * GuiSettings.colorSpeed * 5.0
        shaderColored["u_Color1"] = GuiSettings.shadeColor1
        shaderColored["u_Color2"] = GuiSettings.shadeColor2
        shaderColored["u_Size"] = RenderMain.screenSize / Vec2d(GuiSettings.colorWidth, GuiSettings.colorHeight) / shadeWidthScale

        drawInternal(rect)
    }

    private fun drawInternal(rect: Rect) {
        val pos1 = rect.leftTop
        val pos2 = rect.rightBottom

        vao.use {
            grow(4)

            putQuad(
                vec2(pos1.x, pos1.y).vec2(0.0, 0.0).end(),
                vec2(pos1.x, pos2.y).vec2(0.0, 1.0).end(),
                vec2(pos2.x, pos2.y).vec2(1.0, 1.0).end(),
                vec2(pos2.x, pos1.y).vec2(1.0, 0.0).end()
            )
        }

        vao.upload()
        vao.render()
        vao.clear()
    }
}