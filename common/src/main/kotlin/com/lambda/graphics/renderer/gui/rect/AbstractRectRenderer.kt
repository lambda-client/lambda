package com.lambda.graphics.renderer.gui.rect

import com.lambda.Lambda.mc
import com.lambda.graphics.buffer.vao.vertex.VertexAttrib
import com.lambda.graphics.renderer.IRenderEntry
import com.lambda.graphics.renderer.gui.AbstractGuiRenderer
import com.lambda.graphics.shader.Shader
import com.lambda.module.modules.client.GuiSettings
import com.lambda.util.math.Vec2d
import org.lwjgl.glfw.GLFW.glfwGetTime

abstract class AbstractRectRenderer <T : IRenderEntry<T>> (
    vertexType: VertexAttrib.Group,
    shader: Shader
) : AbstractGuiRenderer<T>(vertexType, shader) {
    override fun preRender() {
        shader["u_Time"] = glfwGetTime() * GuiSettings.colorSpeed * 5.0
        shader["u_Color1"] = GuiSettings.shadeColor1
        shader["u_Color2"] = GuiSettings.shadeColor2

        val screen = Vec2d(mc.window.framebufferWidth, mc.window.framebufferHeight)
        val size = Vec2d(GuiSettings.colorWidth, GuiSettings.colorHeight)
        shader["u_Size"] = screen / size
    }
}