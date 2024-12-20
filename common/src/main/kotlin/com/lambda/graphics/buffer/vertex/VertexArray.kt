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

    override fun map(
        size: Long,
        offset: Long,
        block: (ByteBuffer) -> Unit
    ): Throwable = throw UnsupportedOperationException("Cannot map a vertex array object to memory")

    override fun upload(
        data: ByteBuffer,
        offset: Long,
    ): Throwable = throw UnsupportedOperationException("Data cannot be uploaded to a vertex array object")

    override fun allocate(size: Long) = throw UnsupportedOperationException("Cannot grow a vertex array object")

    override fun bind(id: Int) {
        glBindVertexArray(id); BufferRenderer.currentVertexBuffer = null
    }
}
