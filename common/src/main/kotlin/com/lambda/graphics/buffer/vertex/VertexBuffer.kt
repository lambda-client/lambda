package com.lambda.graphics.buffer.vertex

import com.lambda.graphics.buffer.IBuffer
import com.lambda.graphics.buffer.vertex.attributes.VertexAttrib
import com.lambda.graphics.buffer.vertex.attributes.VertexMode
import com.lambda.graphics.gl.kibibyte
import com.lambda.graphics.gl.putTo
import org.lwjgl.opengl.GL30C.*
import java.nio.ByteBuffer

class VertexBuffer(
    mode: VertexMode,
    attributes: VertexAttrib.Group,
) : IBuffer {
    override val buffers: Int = 2
    override val usage: Int = GL_DYNAMIC_DRAW
    override val target: Int = GL_ARRAY_BUFFER
    override val access: Int = GL_MAP_WRITE_BIT
    override var index = 0
    override val bufferIds = IntArray(buffers).apply { glGenBuffers(this) }

    override fun upload(data: ByteBuffer, offset: Long): Throwable? {
        // Bind the buffer
        bind()

        // Update the buffer data
        val error = map(offset, data.limit().toLong(), data::putTo)

        // We need to swap the index because our memory mapping requires
        // synchronization between the GPU and CPU
        // The GL_MAP_COHERENT bit tells OpenGL to synchronize the transfer
        // to the buffer
        swap()

        // Unbind
        bind(0)

        return error
    }

    init {
        // Fill the buffer with null data
        grow(attributes.stride * mode.indicesCount * 1L.kibibyte)
    }
}
