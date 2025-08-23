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

open class Treed(static: Boolean) {
    val shader = if (static) staticMode.first else dynamicMode.first

    val faces = VertexPipeline(VertexMode.TRIANGLES, if (static) staticMode.second else dynamicMode.second)
    val edges = VertexPipeline(VertexMode.LINES, if (static) staticMode.second else dynamicMode.second)

    var faceBuilder = VertexBuilder(); private set
    var edgeBuilder = VertexBuilder(); private set

    fun upload() {
        faces.upload(faceBuilder)
        edges.upload(edgeBuilder)
    }

    fun render() {
        shader.use()
        shader["u_TickDelta"] = Lambda.mc.partialTicks
        shader["u_CameraPosition"] = Lambda.mc.gameRenderer.camera.pos

        GlStateUtils.withFaceCulling(faces::render)
        GlStateUtils.withLineWidth(StyleEditor.outlineWidth, edges::render)
    }

    fun clear() {
        faces.clear()
        edges.clear()

        faceBuilder = VertexBuilder()
        edgeBuilder = VertexBuilder()
    }

    object Static : Treed(true)
    object Dynamic : Treed(false)

    companion object {
        private val staticMode = shader("renderer/box_static") to VertexAttrib.Group.STATIC_RENDERER
        private val dynamicMode = shader("renderer/box_dynamic") to VertexAttrib.Group.DYNAMIC_RENDERER
    }
}
