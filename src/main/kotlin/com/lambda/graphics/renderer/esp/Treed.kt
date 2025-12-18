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

import com.lambda.Lambda.mc
import com.lambda.graphics.buffer.vertex.attributes.VertexAttrib
import com.lambda.graphics.buffer.vertex.attributes.VertexMode
import com.lambda.graphics.gl.GlStateUtils
import com.lambda.graphics.pipeline.VertexBuilder
import com.lambda.graphics.pipeline.VertexPipeline
import com.lambda.graphics.shader.Shader
import com.lambda.module.modules.client.StyleEditor
import com.lambda.util.extension.partialTicks

/**
 * Open class for 3d rendering. It contains two pipelines, one for edges and the other for faces.
 */
open class Treed(private val static: Boolean) {
    val shader = if (static) staticMode.first else dynamicMode.first

    val faces = VertexPipeline(VertexMode.Triangles, if (static) staticMode.second else dynamicMode.second)
    val edges = VertexPipeline(VertexMode.Lines, if (static) staticMode.second else dynamicMode.second)

    var faceBuilder = VertexBuilder(); private set
    var edgeBuilder = VertexBuilder(); private set

    fun upload() {
        faces.upload(faceBuilder)
        edges.upload(edgeBuilder)
    }

    fun render() {
        shader.use()

        if (!static)
            shader["u_TickDelta"] = mc.partialTicks

        GlStateUtils.withFaceCulling(faces::render)
        GlStateUtils.withLineWidth(StyleEditor.outlineWidth, edges::render)
    }

    fun clear() {
        faces.clear()
        edges.clear()

        faceBuilder = VertexBuilder()
        edgeBuilder = VertexBuilder()
    }

    /**
     * Public object for static rendering. Shapes rendering by this are not interpolated.
     * That means that if the shape is frequently moving, its movement will saccade.
     */
    object Static : Treed(true)

    /**
     * Public object for dynamic rendering. Its position will be interpolated between ticks, allowing
     * for smooth movement at the cost of duplicate position and slightly higher memory consumption.
     */
    object Dynamic : Treed(false)

    companion object {
        private val staticMode = Shader("shaders/vertex/box_static.glsl", "shaders/fragment/pos_color.glsl") to VertexAttrib.Group.STATIC_RENDERER
        private val dynamicMode = Shader("shaders/vertex/box_dynamic.glsl", "shaders/fragment/pos_color.glsl") to VertexAttrib.Group.DYNAMIC_RENDERER
    }
}
