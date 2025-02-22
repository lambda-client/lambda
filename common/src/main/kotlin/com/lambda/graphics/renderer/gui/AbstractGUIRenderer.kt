/*
 * Copyright 2025 Lambda
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

package com.lambda.graphics.renderer.gui

import com.lambda.graphics.RenderMain
import com.lambda.graphics.buffer.VertexPipeline
import com.lambda.graphics.buffer.vertex.attributes.VertexAttrib
import com.lambda.graphics.buffer.vertex.attributes.VertexMode
import com.lambda.graphics.pipeline.ScissorAdapter
import com.lambda.graphics.shader.Shader
import com.lambda.module.modules.client.GuiSettings
import com.lambda.util.math.MathUtils.toInt
import com.lambda.util.math.Rect
import com.lambda.util.math.Vec2d
import org.lwjgl.glfw.GLFW

abstract class AbstractGUIRenderer(
    attribGroup: VertexAttrib.Group,
    val shader: Shader
) {
    private val pipeline = VertexPipeline(VertexMode.TRIANGLES, attribGroup)

    /**
     * Renders GUI elements using the vertex pipeline and shader.
     *
     * This method clears the current vertex pipeline, activates the shader, and then executes the
     * provided lambda [block] to customize vertex data. If [shade] is true, additional shader uniforms
     * are configured for dynamic shading effects—such as time, colors, and size adjustments—for rendering.
     * Finally, the pipeline data is uploaded to the GPU and rendered.
     *
     * @param shade Whether to apply shading effects (default is false).
     * @param block A lambda that operates on the vertex pipeline to customize the rendering.
     */
    protected fun render(
        shade: Boolean = false,
        block: VertexPipeline.() -> Unit
    ) {
        pipeline.clear()
        shader.use()

        block(pipeline)

        shader["u_Shade"] = shade.toInt().toDouble()
        if (shade) {
            shader["u_ShadeTime"] = GLFW.glfwGetTime() * GuiSettings.colorSpeed * 5.0
            shader["u_ShadeColor1"] = GuiSettings.shadeColor1
            shader["u_ShadeColor2"] = GuiSettings.shadeColor2

            shader["u_ShadeSize"] = RenderMain.screenSize / Vec2d(GuiSettings.colorWidth, GuiSettings.colorHeight)
        }

        pipeline.upload()
        pipeline.render()
    }
}