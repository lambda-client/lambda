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
import com.lambda.graphics.buffer.vertex.attributes.VertexAttrib
import com.lambda.graphics.buffer.vertex.attributes.VertexMode
import com.lambda.graphics.pipeline.VertexPipeline
import com.lambda.graphics.shader.Shader.Companion.shader
import com.lambda.graphics.texture.Texture
import com.lambda.module.modules.client.GuiSettings
import com.lambda.module.modules.client.GuiSettings.primaryColor
import com.lambda.module.modules.client.GuiSettings.secondaryColor
import com.lambda.util.math.Rect
import com.lambda.util.math.Vec2d
import org.lwjgl.glfw.GLFW.glfwGetTime

object TextureRenderer {
    private val pipeline = VertexPipeline(VertexMode.TRIANGLES, VertexAttrib.Group.POS_UV)

    private val mainShader = shader("pos_tex")
    private val coloredShader = shader("pos_tex_shady")

    fun drawTexture(texture: Texture, rect: Rect) {
        texture.bind()
        mainShader.use()

        drawInternal(rect)
    }

    fun drawTextureShaded(texture: Texture, rect: Rect) {
        texture.bind()
        coloredShader.use()

        coloredShader["u_Shade"] = 1.0
        coloredShader["u_ShadeTime"] = glfwGetTime() * GuiSettings.colorSpeed * 5.0
        coloredShader["u_ShadeColor1"] = primaryColor
        coloredShader["u_ShadeColor2"] = secondaryColor

        coloredShader["u_ShadeSize"] = RenderMain.screenSize / Vec2d(GuiSettings.colorWidth, GuiSettings.colorHeight)

        drawInternal(rect)
    }

    fun drawInternal(rect: Rect) {
        val pos1 = rect.leftTop
        val pos2 = rect.rightBottom

        pipeline.immediate {
            buildQuad(
                vertex {
                    vec2(pos1.x, pos1.y)
                    vec2(0.0, 0.0)
                },
                vertex {
                    vec2(pos1.x, pos2.y)
                    vec2(0.0, 1.0)
                },
                vertex {
                    vec2(pos2.x, pos2.y)
                    vec2(1.0, 1.0)
                },
                vertex {
                    vec2(pos2.x, pos1.y)
                    vec2(1.0, 0.0)
                }
            )
        }
    }
}
