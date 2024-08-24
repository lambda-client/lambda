package com.lambda.graphics.buffer.pbo

import com.lambda.graphics.buffer.BufferUsage
import org.lwjgl.opengl.GL45C.*
import java.nio.ByteBuffer

class PixelBuffer(
    private val width: Int,
    private val height: Int,
    private val buffers: Int = 2,
    private val bufferUsage: BufferUsage = BufferUsage.DYNAMIC,
) {
    private val pboIds = IntArray(buffers)
    private var writeIdx = 0 // Used to copy pixels from the PBO to the texture
    private var uploadIdx = 0 // Used to upload data to the PBO

    private val queryId = glGenQueries() // Used to measure the time taken to upload data to the PBO
    val uploadTime get() = IntArray(1).also { glGetQueryObjectiv(queryId, GL_QUERY_RESULT, it) }[0]

    fun mapTexture(id: Int, buffer: ByteBuffer) =
        upload(buffer) {
            // Bind the texture
            glBindTexture(GL_TEXTURE_2D, id)

            // Perform the actual data transfer to the GPU
            glTextureSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, 0)
        }

    fun upload(data: ByteBuffer, process: () -> Unit) =
        recordTransfer {
            uploadIdx = (writeIdx + 1) % buffers

            // Bind the next PBO to update pixel values
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, pboIds[writeIdx])

            // Map the buffer into the memory
            val bufferData = glMapBuffer(GL_PIXEL_UNPACK_BUFFER, GL_WRITE_ONLY)
            if (bufferData != null) {
                bufferData.put(data)

                // Release the buffer
                glUnmapBuffer(GL_PIXEL_UNPACK_BUFFER)
            } else {
                println("Failed to map the PBO")
            }

            // Bind the current PBO for writing
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, pboIds[uploadIdx])

            // Copy the pixel values from the PBO to the texture
            process()

            // Unbind the PBO
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0)

            // Swap the indices
            writeIdx = uploadIdx
        }

    private fun recordTransfer(block: () -> Unit) {
        // Start the timer
        glBeginQuery(GL_TIME_ELAPSED, queryId)

        // Perform the transfer
        block()

        // Stop the timer
        glEndQuery(GL_TIME_ELAPSED)
    }

    // Called when no references to the object exist
    fun finalize() {
        // Delete the PBOs
        glDeleteBuffers(pboIds)
    }

    init {
        if (buffers < 1) throw IllegalArgumentException("Buffers must be greater than or equal to 1")

        // Generate the PBOs
        glGenBuffers(pboIds)

        // Fill the buffers with null data to allocate the memory spaces
        repeat(buffers) {
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, pboIds[it])
            glBufferData(GL_PIXEL_UNPACK_BUFFER, width * height * 4L, bufferUsage.gl)
        }

        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0) // Unbind the buffer
    }
}
