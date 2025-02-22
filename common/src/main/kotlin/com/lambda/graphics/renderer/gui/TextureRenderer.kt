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

package com.lambda.graphics.renderer.gui

import com.lambda.graphics.RenderMain
import com.lambda.graphics.buffer.VertexPipeline
import com.lambda.graphics.buffer.vertex.attributes.VertexAttrib
import com.lambda.graphics.buffer.vertex.attributes.VertexMode
import com.lambda.graphics.shader.Shader.Companion.shader
import com.lambda.graphics.texture.Texture
import com.lambda.module.modules.client.GuiSettings
import com.lambda.util.math.Rect
import com.lambda.util.math.Vec2d
import org.lwjgl.glfw.GLFW.glfwGetTime

object TextureRenderer {
    private val pipeline = VertexPipeline(VertexMode.TRIANGLES, VertexAttrib.Group.POS_UV)

    private val mainShader = shader("renderer/pos_tex")
    private val coloredShader = shader("renderer/pos_tex_shady")

    /**
     * Draws the specified texture within the given rectangular area.
     *
     * This function binds the provided texture and activates the main shader before rendering
     * a quad that corresponds to the area defined by the rectangle.
     *
     * @param texture the texture to render.
     * @param rect the area within which the texture should be drawn.
     */
    fun drawTexture(texture: Texture, rect: Rect) {
        texture.bind()
        mainShader.use()

        drawInternal(rect)
    }

    /**
     * Renders a texture with a dynamic shading effect.
     *
     * Binds the given texture and activates a specialized shader that applies animated color shading. 
     * The shading parameters are determined using the current time (via glfwGetTime()) and the settings 
     * from GuiSettings, including color speed, primary and secondary colors, and a size factor calculated 
     * from the screen dimensions. The final rendering is delegated to the internal drawing routine.
     *
     * @param texture the texture to render with shading.
     * @param rect the rectangular area defining where the texture should be drawn.
     */
    fun drawTextureShaded(texture: Texture, rect: Rect) {
        texture.bind()
        coloredShader.use()

        coloredShader["u_Time"] = glfwGetTime() * GuiSettings.colorSpeed * 5.0
        coloredShader["u_Color1"] = GuiSettings.shadeColor1
        coloredShader["u_Color2"] = GuiSettings.shadeColor2
        coloredShader["u_Size"] = RenderMain.screenSize / Vec2d(GuiSettings.colorWidth, GuiSettings.colorHeight)

        drawInternal(rect)
    }

    private fun drawInternal(rect: Rect) {
        val pos1 = rect.leftTop
        val pos2 = rect.rightBottom

        pipeline.use {
            grow(4)

            putQuad(
                vec2(pos1.x, pos1.y).vec2(0.0, 0.0).end(),
                vec2(pos1.x, pos2.y).vec2(0.0, 1.0).end(),
                vec2(pos2.x, pos2.y).vec2(1.0, 1.0).end(),
                vec2(pos2.x, pos1.y).vec2(1.0, 0.0).end()
            )
        }

        pipeline.upload()
        pipeline.render()
        pipeline.clear()
    }
}
