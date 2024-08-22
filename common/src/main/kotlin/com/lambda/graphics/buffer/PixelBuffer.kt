package com.lambda.graphics.buffer

import org.lwjgl.opengl.GL45C.*
import java.nio.ByteBuffer

// NOT TESTED
class PixelBuffer(
    width: Int,
    height: Int,
    buffers: Int = 2
) {
    private val pboIds = IntArray(buffers) { 0 }
    private var index = 0

    fun upload(data: ByteBuffer, block: () -> Unit) {
        // Bind the current PBO for writing
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, pboIds[index])

        // Map the buffer and copy data into it
        val bufferData = glMapBuffer(GL_PIXEL_UNPACK_BUFFER, GL_WRITE_ONLY) as ByteBuffer
        bufferData.put(data)
        glUnmapBuffer(GL_PIXEL_UNPACK_BUFFER)

        // Process the data
        block()

        // Unbind the buffer
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0)

        // Switch to the other PBO
        index = (index + 1) % pboIds.size
    }

    fun download(): ByteBuffer {
        // Bind the current PBO for reading
        glBindBuffer(GL_PIXEL_PACK_BUFFER, pboIds[index])

        // Map the buffer and copy data from it
        val bufferData = glMapBuffer(GL_PIXEL_PACK_BUFFER, GL_READ_ONLY) as ByteBuffer
        val data = bufferData.slice()

        // Unbind the buffer
        glUnmapBuffer(GL_PIXEL_PACK_BUFFER)
        glBindBuffer(GL_PIXEL_PACK_BUFFER, 0)

        return data
    }

    fun finalize() {
        // Delete the PBOs
        glDeleteBuffers(pboIds)
    }

    init {
        // Generate the PBOs
        glGenBuffers(pboIds)

        // Fill the buffers with null data to allocate the memory spaces
        repeat(buffers) {
            glBindBuffer(GL_PIXEL_PACK_BUFFER, pboIds[it])
            glBufferData(GL_PIXEL_PACK_BUFFER, width * height * 4L, GL_DYNAMIC_READ)
        }

        // Unbind the buffer
        glBindBuffer(GL_PIXEL_PACK_BUFFER, 0)
    }
}
