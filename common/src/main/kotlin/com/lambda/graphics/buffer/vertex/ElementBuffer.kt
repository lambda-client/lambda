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

import com.lambda.graphics.buffer.IBuffer
import com.lambda.graphics.buffer.vertex.attributes.VertexMode
import com.lambda.graphics.gl.kibibyte
import com.lambda.graphics.gl.putTo
import org.lwjgl.opengl.GL30C.*
import java.nio.ByteBuffer

class ElementBuffer(mode: VertexMode) : IBuffer {
    override val buffers: Int = 1
    override val usage: Int = GL_DYNAMIC_DRAW
    override val target: Int = GL_ELEMENT_ARRAY_BUFFER
    override val access: Int = GL_MAP_WRITE_BIT
    override var index = 0
    override val bufferIds = intArrayOf(glGenBuffers())

    override fun upload(
        data: ByteBuffer,
        offset: Long,
    ): Throwable? {
        // Bind the buffer
        bind()

        // Map the buffer into the client's memory
        val error = map(offset, data.limit().toLong(), data::putTo)

        // Unbind
        bind(0)

        return error
    }

    override fun bind(id: Int) {
        if (id != 0) prevIbo = lastIbo
        val target = if (id != 0) id else prevIbo

        super.bind(target)
    }

    init {
        // Fill the buffer with null data
        grow(mode.indicesCount * 2L.kibibyte)
    }

    companion object {
        @JvmField
        var lastIbo = 0
        var prevIbo = 0
    }
}
