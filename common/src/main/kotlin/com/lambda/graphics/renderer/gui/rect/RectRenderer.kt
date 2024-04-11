package com.lambda.graphics.renderer.gui.rect

import com.lambda.graphics.buffer.vao.vertex.VertexAttrib
import com.lambda.graphics.renderer.gui.AbstractGuiRenderer
import com.lambda.graphics.shader.Shader
import com.lambda.module.modules.client.GuiSettings
import com.lambda.util.math.Vec2d
import com.mojang.blaze3d.systems.RenderSystem.blendFunc
import com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc
import org.lwjgl.glfw.GLFW.glfwGetTime
import org.lwjgl.opengl.GL11.GL_ONE
import org.lwjgl.opengl.GL11.GL_SRC_ALPHA

class RectRenderer : AbstractGuiRenderer<IRectEntry>(
    VertexAttrib.Group.RECT
) {
    var shadeColor = false
    var fancyBlending = false

    override fun render() {
        shader.use()

        shader["u_Shade"] = shadeColor

        if (shadeColor) {
            shader["u_Time"] = glfwGetTime() * GuiSettings.colorSpeed * 5.0
            shader["u_Color1"] = GuiSettings.shadeColor1
            shader["u_Color2"] = GuiSettings.shadeColor2
            shader["u_Size"] = Vec2d.ONE / Vec2d(GuiSettings.colorWidth, GuiSettings.colorHeight)
        }

        if (fancyBlending) blendFunc(GL_SRC_ALPHA, GL_ONE)
        super.render()
        defaultBlendFunc()
    }

    override fun build(block: IRectEntry.() -> Unit): IRectEntry {
        shadeColor = false
        return super.build(block)
    }

    override fun newEntry(block: IRectEntry.() -> Unit) =
        RectEntry(this, block)

    companion object {
        private val shader = Shader("renderer/rect")
    }
}