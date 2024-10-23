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

package com.lambda.graphics.gl

import net.minecraft.client.render.BufferRenderer
import org.lwjgl.opengl.GL30C.*
import java.nio.ByteBuffer

object VaoUtils {
    @JvmField
    var lastIbo = 0
    private var prevIbo = 0

    fun bindVertexArray(vao: Int) {
        glBindVertexArray(vao)
        BufferRenderer.currentVertexBuffer = null
    }

    fun bindVertexBuffer(vbo: Int) =
        glBindBuffer(GL_ARRAY_BUFFER, vbo)

    fun bindIndexBuffer(ibo: Int) {
        if (ibo != 0) prevIbo = lastIbo
        val targetIbo = if (ibo != 0) ibo else prevIbo
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, targetIbo)
    }

    fun unbindVertexArray() =
        bindVertexArray(0)

    fun unbindVertexBuffer() =
        bindVertexBuffer(0)

    fun unbindIndexBuffer() =
        bindIndexBuffer(0)

    fun enableVertexAttribute(i: Int) =
        glEnableVertexAttribArray(i)

    fun vertexAttribute(index: Int, size: Int, type: Int, normalized: Boolean, stride: Int, pointer: Long) =
        glVertexAttribPointer(index, size, type, normalized, stride, pointer)

    fun bufferData(target: Int, data: ByteBuffer, usage: Int) =
        glBufferData(target, data, usage)
}
