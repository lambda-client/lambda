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

package com.lambda.graphics.renderer.esp

import com.lambda.Lambda
import com.lambda.graphics.buffer.vertex.attributes.VertexAttrib
import com.lambda.graphics.buffer.vertex.attributes.VertexMode
import com.lambda.graphics.gl.GlStateUtils
import com.lambda.graphics.pipeline.VertexBuilder
import com.lambda.graphics.pipeline.VertexPipeline
import com.lambda.graphics.shader.Shader.Companion.shader
import com.lambda.module.modules.client.StyleEditor
import com.lambda.util.extension.partialTicks

object Treed {
    val staticShader = shader("renderer/box_static")
    val dynamicShader = shader("renderer/box_dynamic")

    val staticFaces = VertexPipeline(VertexMode.TRIANGLES, VertexAttrib.Group.STATIC_RENDERER)
    val staticEdges = VertexPipeline(VertexMode.LINES, VertexAttrib.Group.STATIC_RENDERER)
    val dynamicFaces = VertexPipeline(VertexMode.TRIANGLES, VertexAttrib.Group.DYNAMIC_RENDERER)
    val dynamicEdges = VertexPipeline(VertexMode.LINES, VertexAttrib.Group.DYNAMIC_RENDERER)

    // Vertex builders for shape building
    var staticFaceBuilder = VertexBuilder(); private set
    var dynamicFaceBuilder = VertexBuilder(); private set
    var staticEdgeBuilder = VertexBuilder(); private set
    var dynamicEdgeBuilder = VertexBuilder(); private set

    fun upload() {
        staticFaces.upload(staticFaceBuilder)
        staticEdges.upload(staticEdgeBuilder)
        dynamicFaces.upload(dynamicFaceBuilder)
        dynamicEdges.upload(dynamicEdgeBuilder)
    }

    fun render() {
        staticShader.use()
        staticShader["u_TickDelta"] = Lambda.mc.partialTicks
        staticShader["u_CameraPosition"] = Lambda.mc.gameRenderer.camera.pos

        GlStateUtils.withFaceCulling(staticFaces::render)
        GlStateUtils.withLineWidth(StyleEditor.outlineWidth, staticEdges::render)

        dynamicShader.use()
        dynamicShader["u_TickDelta"] = Lambda.mc.partialTicks
        dynamicShader["u_CameraPosition"] = Lambda.mc.gameRenderer.camera.pos

        GlStateUtils.withFaceCulling(dynamicFaces::render)
        GlStateUtils.withLineWidth(StyleEditor.outlineWidth, dynamicEdges::render)
    }

    fun clear() {
        staticFaces.clear()
        staticEdges.clear()
        dynamicFaces.clear()
        dynamicEdges.clear()

        staticFaceBuilder = VertexBuilder()
        staticEdgeBuilder = VertexBuilder()
        dynamicFaceBuilder = VertexBuilder()
        dynamicEdgeBuilder = VertexBuilder()
    }
}
