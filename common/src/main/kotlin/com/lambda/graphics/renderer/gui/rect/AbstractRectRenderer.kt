package com.lambda.graphics.renderer.gui.rect

import com.lambda.graphics.RenderMain
import com.lambda.graphics.buffer.vao.VAO
import com.lambda.graphics.buffer.vao.vertex.VertexAttrib
import com.lambda.graphics.buffer.vao.vertex.VertexMode
import com.lambda.graphics.shader.Shader
import com.lambda.module.modules.client.GuiSettings
import com.lambda.util.math.Vec2d
import org.lwjgl.glfw.GLFW.glfwGetTime

abstract class AbstractRectRenderer(
    attribGroup: VertexAttrib.Group,
    val shader: Shader
) {
    protected val vao = VAO(VertexMode.TRIANGLES, attribGroup)

    fun render() {
        shader.use()
        shader["u_Time"] = glfwGetTime() * GuiSettings.colorSpeed * 5.0
        shader["u_Color1"] = GuiSettings.shadeColor1
        shader["u_Color2"] = GuiSettings.shadeColor2

        shader["u_Size"] = RenderMain.screenSize / Vec2d(GuiSettings.colorWidth, GuiSettings.colorHeight)

        vao.upload()
        vao.render()
        vao.clear()
    }
}