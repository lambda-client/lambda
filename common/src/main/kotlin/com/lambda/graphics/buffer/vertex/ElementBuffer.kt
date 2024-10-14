package com.lambda.graphics.buffer.vertex

import com.lambda.graphics.buffer.IBuffer
import com.lambda.graphics.buffer.vertex.attributes.VertexMode
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
        grow(mode.indicesCount * 512 * 4L)
    }

    companion object {
        @JvmField
        var lastIbo = 0
        var prevIbo = 0
    }
}
