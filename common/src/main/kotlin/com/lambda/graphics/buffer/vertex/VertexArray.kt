package com.lambda.graphics.buffer.vertex

import com.lambda.graphics.buffer.IBuffer
import net.minecraft.client.render.BufferRenderer
import org.lwjgl.opengl.GL30C.*
import java.nio.ByteBuffer

class VertexArray : IBuffer {
    override val buffers: Int = 1
    override val usage: Int = -1
    override val target: Int = -1
    override val access: Int = -1
    override var index = 0
    override val bufferIds = intArrayOf(glGenVertexArrays())

    override fun map(
        offset: Long,
        size: Long,
        block: (ByteBuffer) -> Unit
    ): Throwable? = throw UnsupportedOperationException("Cannot map a vertex array object to memory")

    override fun upload(
        data: ByteBuffer,
        offset: Long,
    ): Throwable? = throw UnsupportedOperationException("Data cannot be uploaded to a vertex array object")

    override fun grow(size: Long) = throw UnsupportedOperationException("Cannot grow a vertex array object")

    override fun bind(id: Int) { glBindVertexArray(id); BufferRenderer.currentVertexBuffer = null }
}
