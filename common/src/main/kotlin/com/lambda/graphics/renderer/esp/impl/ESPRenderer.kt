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

package com.lambda.graphics.renderer.esp.impl

import com.lambda.Lambda.mc
import com.lambda.core.TimerManager
import com.lambda.graphics.buffer.VertexPipeline
import com.lambda.graphics.buffer.vertex.attributes.VertexAttrib
import com.lambda.graphics.buffer.vertex.attributes.VertexMode
import com.lambda.graphics.gl.GlStateUtils.withFaceCulling
import com.lambda.graphics.gl.GlStateUtils.withLineWidth
import com.lambda.graphics.shader.Shader
import com.lambda.module.modules.client.RenderSettings
import com.lambda.util.extension.partialTicks

abstract class ESPRenderer(tickedMode: Boolean) {
    val shader: Shader
    val faces: VertexPipeline
    val outlines: VertexPipeline

    init {
        val mode = if (tickedMode) dynamicMode else staticMode

        shader = mode.first
        faces = VertexPipeline(VertexMode.TRIANGLES, mode.second)
        outlines = VertexPipeline(VertexMode.LINES, mode.second)
    }

    open fun upload() {
        faces.upload()
        outlines.upload()
    }

    fun render() {
        shader.use()
        shader["u_TickDelta"] = TimerManager.fixedTickDelta
        shader["u_CameraPosition"] = mc.gameRenderer.camera.pos

        withFaceCulling(faces::render)
        withLineWidth(RenderSettings.outlineWidth, outlines::render)
    }

    open fun clear() {
        faces.clear()
        outlines.clear()
    }

    companion object {
        private val staticMode = Shader(
            "renderer/pos_color",
            "renderer/box_static"
        ) to VertexAttrib.Group.STATIC_RENDERER

        private val dynamicMode = Shader(
            "renderer/pos_color",
            "renderer/box_dynamic"
        ) to VertexAttrib.Group.DYNAMIC_RENDERER
    }
}
