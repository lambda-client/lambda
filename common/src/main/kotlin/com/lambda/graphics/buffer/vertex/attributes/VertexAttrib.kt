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

sealed class VertexAttrib(
    private val componentCount: Int,
    componentSize: Int,
    private val normalized: Boolean,
    private val type: Int
) {
    open class Float(
        normalized: Boolean = false
    ) : VertexAttrib(1, 4, normalized, GL_FLOAT) {
        companion object : Float()
    }

    open class Vec2(
        normalized: Boolean = false
    ) : VertexAttrib(2, 4, normalized, GL_FLOAT) {
        companion object : Vec2()
    }

    open class Vec3(
        normalized: Boolean = false
    ) : VertexAttrib(3, 4, normalized, GL_FLOAT) {
        companion object : Vec3()
    }

    open class Color(
        normalized: Boolean = true
    ) : VertexAttrib(4, 1, normalized, GL_UNSIGNED_BYTE) {
        companion object : Color()
    }

    val size = componentCount * componentSize

    fun link(index: Int, pointer: Long, stride: Int) {
        glEnableVertexAttribArray(index)
        glVertexAttribPointer(index, componentCount, type, normalized, stride, pointer)
    }

    @Suppress("ClassName")
    open class Group(vararg val attributes: VertexAttrib) {
        object POS_UV : Group(
            Vec2, Vec2
        )

        // GUI
        object FONT : Group(
            Vec2, Vec2, Color
        )

        // WORLD
        object DYNAMIC_RENDERER : Group(
            Vec3, Vec3, Color
        )

        object STATIC_RENDERER : Group(
            Vec3, Color
        )

        object PARTICLE : Group(
            Vec3, Vec2, Color
        )

        val stride = attributes.sumOf { attribute ->
            attribute.size
        }

        fun link() {
            attributes.foldIndexed(0L) { index, pointer, attrib ->
                attrib.link(index, pointer, stride)
                pointer + attrib.size
            }
        }
    }
}
