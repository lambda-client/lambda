package com.lambda.graphics.buffer.pbo

import com.lambda.Lambda.LOG
import com.lambda.graphics.buffer.BufferUsage
import org.lwjgl.opengl.GL
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
    var transferRate = 0L // The transfer rate in bytes per second
        private set

    var pboSupported = false
        private set

    fun mapTexture(id: Int, buffer: ByteBuffer) =
        upload(buffer) {
            // Bind the texture
            glBindTexture(GL_TEXTURE_2D, id)

            if (buffers > 0 && pboSupported) {
                // Bind the next PBO to update pixel values
                glBindBuffer(GL_PIXEL_UNPACK_BUFFER, pboIds[writeIdx])

                // Perform the actual data transfer to the GPU
                glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, 0)
            }
            else {
                // Perform the actual data transfer to the GPU
                glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, buffer)
            }
        }

    fun upload(data: ByteBuffer, process: () -> Unit) =
        recordTransfer {
            if (buffers >= 2)
                uploadIdx = (writeIdx + 1) % buffers

            // Copy the pixel values from the PBO to the texture
            process()

            if (!pboSupported) return@recordTransfer

            // Bind the current PBO for writing
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, pboIds[uploadIdx])

            // Note that glMapBuffer() causes sync issue.
            // If GPU is working with this buffer, glMapBuffer() will wait(stall)
            // until GPU to finish its job. To avoid waiting (idle), you can call
            // first glBufferData() with NULL pointer before glMapBuffer().
            // If you do that, the previous data in PBO will be discarded and
            // glMapBuffer() returns a new allocated pointer immediately
            // even if GPU is still working with the previous data.
            glBufferData(GL_PIXEL_UNPACK_BUFFER, width * height * 4L, bufferUsage.gl)

            // Map the buffer into the memory
            val bufferData = glMapBuffer(GL_PIXEL_UNPACK_BUFFER, GL_WRITE_ONLY)
            if (bufferData != null) {
                bufferData.put(data)

                // Release the buffer
                glUnmapBuffer(GL_PIXEL_UNPACK_BUFFER)
            } else throw IllegalStateException("Failed to map the buffer")

            // Unbind the PBO
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0)

            // Swap the indices
            writeIdx = uploadIdx
        }

    private fun recordTransfer(block: () -> Unit) {
        if (!pboSupported) {
            block()
            return
        }

        // Start the timer
        glBeginQuery(GL_TIME_ELAPSED, queryId)

        // Perform the transfer
        block()

        // Stop the timer
        glEndQuery(GL_TIME_ELAPSED)

        // Calculate the transfer rate
        val time = uploadTime
        if (time > 0) {
            transferRate = (width * height * 4L * 1_000_000_000) / time
        }
    }

    // Called when no references to the object exist
    fun finalize() {
        // Delete the PBOs
        glDeleteBuffers(pboIds)
    }

    init {
        // Check if the PBO is supported
        GL.getCapabilities().let { pboSupported = it.OpenGL30 || it.GL_ARB_pixel_buffer_object }

        if (!pboSupported && buffers > 0)
            LOG.warn("Client tried to utilize PBOs, but they are not supported on the machine, falling back to direct buffer upload")

        if (buffers < 0) throw IllegalArgumentException("Buffers must be greater than or equal to 0")

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
