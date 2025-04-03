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

package com.lambda.graphics.renderer.esp

import com.lambda.Lambda
import com.lambda.graphics.pipeline.VertexPipeline
import com.lambda.graphics.buffer.vertex.attributes.VertexAttrib
import com.lambda.graphics.buffer.vertex.attributes.VertexMode
import com.lambda.graphics.gl.GlStateUtils
import com.lambda.graphics.pipeline.VertexBuilder
import com.lambda.graphics.shader.Shader
import com.lambda.graphics.shader.Shader.Companion.shader
import com.lambda.module.modules.client.RenderSettings
import com.lambda.util.extension.partialTicks

open class ESPRenderer(tickedMode: Boolean) {
    val shader: Shader

    val faces: VertexPipeline
    var faceBuilder: VertexBuilder

    val outlines: VertexPipeline
    var outlineBuilder: VertexBuilder

    init {
        val mode = if (tickedMode) dynamicMode else staticMode
        shader = mode.first

        faces = VertexPipeline(VertexMode.TRIANGLES, mode.second)
        faceBuilder = faces.build()

        outlines = VertexPipeline(VertexMode.LINES, mode.second)
        outlineBuilder = outlines.build()
    }

    fun upload() {
        faces.upload(faceBuilder)
        outlines.upload(outlineBuilder)
    }

    fun render() {
        shader.use()
        shader["u_TickDelta"] = Lambda.mc.partialTicks
        shader["u_CameraPosition"] = Lambda.mc.gameRenderer.camera.pos

        GlStateUtils.withFaceCulling(faces::render)
        GlStateUtils.withLineWidth(RenderSettings.outlineWidth, outlines::render)
    }

    fun clear() {
        faces.clear()
        outlines.clear()
        faceBuilder = faces.build()
        outlineBuilder = outlines.build()
    }

    companion object {
        private val staticMode = shader(
            "renderer/box_static"
        ) to VertexAttrib.Group.STATIC_RENDERER

        private val dynamicMode = shader(
            "renderer/box_dynamic"
        ) to VertexAttrib.Group.DYNAMIC_RENDERER
    }
}