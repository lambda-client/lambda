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

package com.lambda.graphics.buffer.vertex

import com.lambda.graphics.buffer.Buffer
import com.lambda.graphics.buffer.vertex.attributes.VertexAttrib
import com.lambda.graphics.buffer.vertex.attributes.VertexMode
import com.lambda.graphics.pipeline.PersistentBuffer
import org.lwjgl.opengl.GL30C.GL_UNSIGNED_INT
import org.lwjgl.opengl.GL30C.glBindVertexArray
import org.lwjgl.opengl.GL32C.glDrawElementsBaseVertex
import java.nio.ByteBuffer

class VertexArray(
    private val vertexMode: VertexMode,
    private val attributes: VertexAttrib.Group
) : Buffer(isVertexArray = true) {
    override val usage: Int = -1
    override val target: Int = -1
    override val access: Int = -1

    private var linkedVBO: PersistentBuffer? = null

    fun renderIndices(
        ibo: PersistentBuffer
    ) = linkedVBO?.let { vbo ->
        renderInternal(
            indicesSize = ibo.byteBuffer.bytesPut - ibo.uploadOffset,
            indicesPointer = ibo.byteBuffer.pointer + ibo.uploadOffset,
            verticesOffset = vbo.uploadOffset
        )
    } ?: throw IllegalStateException("Unable to use vertex array without having a VBO linked to it.")

    private fun renderInternal(
        indicesSize: Long,
        indicesPointer: Long,
        verticesOffset: Long
    ) = bind {
        glDrawElementsBaseVertex(
            vertexMode.mode,
            indicesSize.toInt() / Int.SIZE_BYTES,
            GL_UNSIGNED_INT,
            indicesPointer,
            verticesOffset.toInt() / attributes.stride,
        )
    }

    fun linkVbo(vbo: PersistentBuffer, block: VertexArray.() -> Unit = {  }) {
        linkedVBO = vbo

        bind {
            vbo.use {
                attributes.link()
                block(this@VertexArray)
            }
        }
    }

    override fun map(size: Long, offset: Long, block: (ByteBuffer) -> Unit) = throw UnsupportedOperationException()
    override fun upload(data: ByteBuffer, offset: Long) = throw UnsupportedOperationException()

    override fun bind(id: Int) = glBindVertexArray(id)
}
