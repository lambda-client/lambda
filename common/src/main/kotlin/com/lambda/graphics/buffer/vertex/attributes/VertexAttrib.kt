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

package com.lambda.graphics.buffer.vertex.attributes

import org.lwjgl.opengl.GL11C.GL_FLOAT
import org.lwjgl.opengl.GL11C.GL_UNSIGNED_BYTE
import org.lwjgl.opengl.GL20C.glEnableVertexAttribArray
import org.lwjgl.opengl.GL20C.glVertexAttribPointer

enum class VertexAttrib(
    private val componentCount: Int,
    componentSize: Int,
    private val normalized: Boolean,
    private val type: Int
) {
    Float(1, 4, false, GL_FLOAT),
    Vec2(2, 4, false, GL_FLOAT),
    Vec3(3, 4, false, GL_FLOAT),
    Color(4, 1, true, GL_UNSIGNED_BYTE);

    val size = componentCount * componentSize

    fun pointer(index: Int, pointer: Long, stride: Int) {
        glEnableVertexAttribArray(index)
        glVertexAttribPointer(index, componentCount, type, normalized, stride, pointer)
    }

    enum class Group(vararg val attributes: VertexAttrib) {
        POS_UV(Vec2, Vec2),

        // GUI
        FONT(
            Vec3, // pos
            Vec2, // uv
            Color
        ),

        RECT_FILLED(
            Vec3, // pos
            Vec2, // uv
            Color
        ),

        RECT_OUTLINE(
            Vec3, // pos
            Vec2, // uv
            Float, // alpha
            Color
        ),

        // WORLD
        DYNAMIC_RENDERER(
            Vec3, // prev pos
            Vec3, // pos
            Color
        ),

        STATIC_RENDERER(
            Vec3, // pos
            Color
        ),

        PARTICLE(
            Vec3,
            Vec2, // pos
            Color
        );

        val stride = attributes.sumOf { attribute ->
            attribute.size
        }

        fun link() {
            attributes.foldIndexed(0L) { index, pointer, attrib ->
                attrib.pointer(index, pointer, stride)
                pointer + attrib.size
            }
        }
    }
}

