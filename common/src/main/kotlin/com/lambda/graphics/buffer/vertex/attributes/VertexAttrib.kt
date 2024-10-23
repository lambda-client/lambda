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

package com.lambda.graphics.buffer.vao.vertex

import com.lambda.graphics.gl.GLObject
import org.lwjgl.opengl.GL11C.GL_FLOAT
import org.lwjgl.opengl.GL11C.GL_UNSIGNED_BYTE

enum class VertexAttrib(
    val componentCount: Int,
    componentSize: Int,
    val normalized: Boolean,
    override val gl: Int
) : GLObject {
    Float(1, 4, false, GL_FLOAT),
    Vec2(2, 4, false, GL_FLOAT),
    Vec3(3, 4, false, GL_FLOAT),
    Color(4, 1, true, GL_UNSIGNED_BYTE);

    val size = componentCount * componentSize

    enum class Group(vararg val attributes: VertexAttrib) {
        POS_UV(Vec2, Vec2),

        // GUI
        FONT(Vec3, Vec2, Color), // pos, uv, color
        RECT_FILLED(Vec2, Vec2, Vec2, Float, Float, Color), // pos, uv, size, roundRadius, shade, color
        RECT_OUTLINE(Vec2, Float, Float, Color), // pos, alpha, shade, color

        // WORLD
        DYNAMIC_RENDERER(Vec3, Vec3, Color), // prev pos, pos, color
        STATIC_RENDERER(Vec3, Color), // pos, color

        PARTICLE(Vec3, Vec2, Color); // pos, uv, color

        val stride = attributes.sumOf { attribute -> attribute.size }
    }
}
