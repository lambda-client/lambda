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

import com.lambda.graphics.buffer.IRenderContext
import com.lambda.graphics.renderer.esp.ESPRenderer
import java.awt.Color
import java.util.concurrent.ConcurrentHashMap

open class StaticESPRenderer(private val useVertexCaching: Boolean = true) : ESPRenderer(false) {
    val faceVertices = ConcurrentHashMap<Vertex, Int>()
    val outlineVertices = ConcurrentHashMap<Vertex, Int>()

    var updateFaces = false
    var updateOutlines = false

    override fun upload() {
        if (updateFaces) {
            updateFaces = false
            faces.upload()
        }

        if (updateOutlines) {
            updateOutlines = false
            outlines.upload()
        }
    }

    override fun clear() {
        faceVertices.clear()
        outlineVertices.clear()
        super.clear()
    }

    fun IRenderContext.vertex(
        storage: MutableMap<Vertex, Int>,
        x: Double, y: Double, z: Double,
        color: Color
    ) = lazy {
        val vtx = { vec3(x, y, z).color(color).end() }
        if (!useVertexCaching) return@lazy vtx()

        storage.getOrPut(Vertex(x, y, z, color), vtx)
    }

    data class Vertex(val x: Double, val y: Double, val z: Double, val color: Color)
}
