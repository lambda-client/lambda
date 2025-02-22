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
import net.minecraft.client.render.BufferRenderer
import org.lwjgl.opengl.GL30C.*
import java.nio.ByteBuffer

class VertexArray : Buffer(isVertexArray = true) {
    override val usage: Int = -1
    override val target: Int = -1
    override val access: Int = -1

    /**
     * Throws an UnsupportedOperationException to indicate that memory mapping is not supported for vertex array objects.
     *
     * @param size the intended size of the memory mapping.
     * @param offset the intended offset in the buffer.
     * @param block the callback to process the mapped ByteBuffer (unused).
     * @throws UnsupportedOperationException always thrown to indicate that mapping is not supported.
     */
    override fun map(
        size: Long,
        offset: Long,
        block: (ByteBuffer) -> Unit
    ): Throwable = throw UnsupportedOperationException("Cannot map a vertex array object to memory")

    /**
     * Attempts to upload data to the vertex array object.
     *
     * This operation is not supported for vertex array objects and always throws an
     * UnsupportedOperationException.
     *
     * @param data the buffer containing the data to upload (unused)
     * @param offset the offset at which data would have been uploaded (unused)
     * @throws UnsupportedOperationException always thrown to indicate that data uploads are not supported for vertex array objects.
     */
    override fun upload(
        data: ByteBuffer,
        offset: Long,
    ): Throwable = throw UnsupportedOperationException("Data cannot be uploaded to a vertex array object")

    /**
 * Throws an UnsupportedOperationException because vertex array objects cannot be resized.
 *
 * @param size The requested allocation size, which is ignored because growing a vertex array is unsupported.
 * @throws UnsupportedOperationException always thrown to indicate the operation is not supported.
 */
override fun allocate(size: Long) = throw UnsupportedOperationException("Cannot grow a vertex array object")

    /**
     * Binds the vertex array object using the specified identifier.
     *
     * This method calls `glBindVertexArray` to bind the vertex array and resets the current vertex buffer binding.
     *
     * @param id the OpenGL identifier of the vertex array.
     */
    override fun bind(id: Int) {
        glBindVertexArray(id); BufferRenderer.currentVertexBuffer = null
    }
}
