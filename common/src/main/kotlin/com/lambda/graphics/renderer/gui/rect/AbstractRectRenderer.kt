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

package com.lambda.graphics.renderer.gui.rect

import com.lambda.graphics.RenderMain
import com.lambda.graphics.buffer.VertexPipeline
import com.lambda.graphics.buffer.vertex.attributes.VertexAttrib
import com.lambda.graphics.buffer.vertex.attributes.VertexMode
import com.lambda.graphics.shader.Shader
import com.lambda.module.modules.client.GuiSettings
import com.lambda.util.math.Vec2d
import org.lwjgl.glfw.GLFW.glfwGetTime

abstract class AbstractRectRenderer(
    attribGroup: VertexAttrib.Group,
    val shader: Shader
) {
    protected val pipeline = VertexPipeline(VertexMode.TRIANGLES, attribGroup)

    fun render() {
        shader.use()
        shader["u_Time"] = glfwGetTime() * GuiSettings.colorSpeed * 5.0
        shader["u_Color1"] = GuiSettings.shadeColor1
        shader["u_Color2"] = GuiSettings.shadeColor2

        shader["u_Size"] = RenderMain.screenSize / Vec2d(GuiSettings.colorWidth, GuiSettings.colorHeight)

        pipeline.upload()
        pipeline.render()
        pipeline.clear()
    }
}
