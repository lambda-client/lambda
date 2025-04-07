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

import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.graphics.RenderMain
import com.lambda.graphics.pipeline.VertexPipeline
import com.lambda.graphics.buffer.vertex.attributes.VertexAttrib
import com.lambda.graphics.buffer.vertex.attributes.VertexMode
import com.lambda.graphics.shader.Shader
import com.lambda.module.modules.client.GuiSettings
import com.lambda.module.modules.client.GuiSettings.primaryColor
import com.lambda.module.modules.client.GuiSettings.secondaryColor
import com.lambda.module.modules.client.RenderSettings
import com.lambda.util.math.MathUtils.toInt
import com.lambda.util.math.Vec2d
import org.lwjgl.glfw.GLFW

open class AbstractGUIRenderer(
    attribGroup: VertexAttrib.Group,
    val shader: Shader
) {
    private val pipeline = VertexPipeline(VertexMode.TRIANGLES, attribGroup)
    private var memoryMapping = true

    init {
        listen<TickEvent.Render.Pre>(alwaysListen = true) {
            memoryMapping = RenderSettings.useMemoryMapping
        }

        listen<TickEvent.Render.Post>(alwaysListen = true) {
            if (memoryMapping) pipeline.sync()
        }
    }

    fun render(
        shade: Boolean = false,
        block: VertexPipeline.(Shader) -> Unit
    ) {
        shader.use()

        block(pipeline, shader)

        shader["u_Shade"] = shade.toInt().toDouble()
        if (shade) {
            shader["u_ShadeTime"] = GLFW.glfwGetTime() * GuiSettings.colorSpeed * 5.0
            shader["u_ShadeColor1"] = primaryColor
            shader["u_ShadeColor2"] = secondaryColor

            shader["u_ShadeSize"] = RenderMain.screenSize / Vec2d(GuiSettings.colorWidth, GuiSettings.colorHeight)
        }

        pipeline.apply {
            render()
            if (memoryMapping) end() else clear()
        }
    }
}