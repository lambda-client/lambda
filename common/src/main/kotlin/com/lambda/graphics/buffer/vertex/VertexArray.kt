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

package com.lambda.graphics.buffer.vertex

import com.lambda.graphics.buffer.Buffer
import com.lambda.graphics.buffer.vertex.attributes.VertexAttrib
import com.lambda.graphics.buffer.vertex.attributes.VertexMode
import net.minecraft.client.render.BufferRenderer
import org.lwjgl.opengl.GL30C.*
import org.lwjgl.opengl.GL32C.glDrawElementsBaseVertex
import java.nio.ByteBuffer

class VertexArray(
    private val vertexMode: VertexMode,
    private val attributes: VertexAttrib.Group
) : Buffer(isVertexArray = true) {
    override val usage: Int = -1
    override val target: Int = -1
    override val access: Int = -1

    fun render(
        indicesSize: Long,
        indicesPointer: Long,
        verticesOffset: Int
    ) {
        bind()
        glDrawElementsBaseVertex(
            vertexMode.mode,
            indicesSize.toInt() / UInt.SIZE_BYTES,
            GL_UNSIGNED_INT,
            indicesPointer,
            verticesOffset / attributes.stride
        )
        bind(0)
    }

    override fun map(size: Long, offset: Long, block: (ByteBuffer) -> Unit) = throw UnsupportedOperationException()
    override fun upload(data: ByteBuffer, offset: Long) = throw UnsupportedOperationException()

    override fun bind(id: Int) {
        glBindVertexArray(id); BufferRenderer.currentVertexBuffer = null
    }
}
