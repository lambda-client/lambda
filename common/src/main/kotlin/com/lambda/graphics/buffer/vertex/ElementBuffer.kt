package com.lambda.graphics.buffer.vertex

import com.lambda.graphics.buffer.IBuffer
import com.lambda.graphics.buffer.vertex.attributes.VertexMode
import com.lambda.graphics.gl.VaoUtils
import com.lambda.graphics.gl.putTo
import org.lwjgl.opengl.GL30C.*
import java.nio.ByteBuffer

class ElementBuffer(mode: VertexMode) : IBuffer {
    override val buffers: Int = 1
    override val usage: Int = GL_DYNAMIC_DRAW
    override val target: Int = GL_ELEMENT_ARRAY_BUFFER
    override val access: Int = GL_MAP_WRITE_BIT
    override var index = 0
    override val bufferIds = IntArray(buffers).also { glGenBuffers(it) }

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

    override fun bind(id: Int) = super.bind(VaoUtils.lastIbo)

    init {
        // Fill the buffer with null data
        grow(mode.indicesCount * 512 * 4L)
    }
}
