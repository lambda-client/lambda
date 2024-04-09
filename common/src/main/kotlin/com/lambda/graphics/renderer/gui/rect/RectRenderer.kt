package com.lambda.graphics.renderer.gui.rect

import com.lambda.graphics.buffer.vao.vertex.VertexAttrib
import com.lambda.graphics.renderer.gui.AbstractGuiRenderer
import com.lambda.graphics.shader.Shader
import com.lambda.module.modules.client.ClickGui
import com.lambda.util.math.Vec2d
import org.lwjgl.glfw.GLFW.glfwGetTime

class RectRenderer : AbstractGuiRenderer<IRectEntry>(
    VertexAttrib.Group.RECT
) {
    var shadeColor = false

    override fun render() {
        shader.use()

        shader["u_Shade"] = shadeColor

        if (shadeColor) {
            shader["u_Time"] = glfwGetTime() * ClickGui.colorSpeed * 3.0
            shader["u_Color1"] = ClickGui.shadeColor1
            shader["u_Color2"] = ClickGui.shadeColor2
            shader["u_Size"] = Vec2d.ONE / Vec2d(ClickGui.colorWidth, ClickGui.colorHeight)
        }

        super.render()
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