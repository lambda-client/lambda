package com.lambda.graphics.buffer.pbo

import com.lambda.Lambda.LOG
import com.lambda.graphics.buffer.BufferUsage
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL45C.*
import java.nio.ByteBuffer

/**
 * Represents a Pixel Buffer Object (PBO) that facilitates asynchronous data transfer to the GPU.
 * This class manages the creation, usage, and cleanup of PBOs and provides methods to map textures and upload data efficiently.
 *
 * @property width The width of the texture in pixels.
 * @property height The height of the texture in pixels.
 * @property buffers The number of PBOs to be used. Default is 2, which allows double buffering.
 * @property bufferUsage The usage pattern of the buffer, indicating how the buffer will be used (static, dynamic, etc.).
 */
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
    private val uploadTime get() =
        IntArray(1).also { glGetQueryObjectiv(queryId, GL_QUERY_RESULT, it) }.first()
    private var transferRate = 0L // The transfer rate in bytes per second

    private val pboSupported = GL.getCapabilities().OpenGL30 || GL.getCapabilities().GL_ARB_pixel_buffer_object

    private var initialDataSent: Boolean = false

    /**
     * Maps the given texture ID to the buffer and performs the necessary operations to upload the texture data.
     *
     * @param id The texture ID to which the buffer will be mapped.
     * @param buffer The [ByteBuffer] containing the pixel data to be uploaded to the texture.
     */
    fun mapTexture(id: Int, buffer: ByteBuffer) {
        if (!initialDataSent) {
            glBindTexture(GL_TEXTURE_2D, id)
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0)
            glBindTexture(GL_TEXTURE_2D, 0)

            initialDataSent = true

            return
        }

        upload(buffer) {
            // Bind the texture
            glBindTexture(GL_TEXTURE_2D, id)

            if (buffers > 0 && pboSupported) {
                // Bind the next PBO to update pixel values
                glBindBuffer(GL_PIXEL_UNPACK_BUFFER, pboIds[writeIdx])

                // Perform the actual data transfer to the GPU
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0)
            } else {
                // Perform the actual data transfer to the GPU
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer)
            }

            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)

            // Unbind the texture
            glBindTexture(GL_TEXTURE_2D, 0)
        }
    }

    /**
     * Uploads the given pixel data to the PBO and executes the provided processing function to manage the PBO's data transfer.
     *
     * @param data The [ByteBuffer] containing the pixel data to be uploaded.
     * @param process A lambda function to execute after uploading the data to manage the PBO's data transfer.
     */
    fun upload(data: ByteBuffer, process: () -> Unit) =
        recordTransfer {
            if (buffers >= 2)
                uploadIdx = (writeIdx + 1) % buffers

            // Copy the pixel values from the PBO to the texture
            process()

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

    /**
     * Measures and records the time taken to transfer data to the PBO, calculating the transfer rate in bytes per second.
     *
     * @param block A lambda function representing the block of code where the transfer occurs.
     */
    private fun recordTransfer(block: () -> Unit) {
        // Start the timer
        glBeginQuery(GL_TIME_ELAPSED, queryId)

        // Perform the transfer
        block()

        // Stop the timer
        glEndQuery(GL_TIME_ELAPSED)

        // Calculate the transfer rate
        val time = uploadTime
        if (time > 0) transferRate = (width * height * 4L * 1_000_000_000) / time
    }

    /**
     * Cleans up resources by deleting the PBOs when the object is no longer in use.
     */
    fun finalize() {
        // Delete the PBOs
        glDeleteBuffers(pboIds)
    }

    /**
     * Initializes the PBOs, allocates memory for them, and handles unsupported PBO scenarios.
     *
     * @throws IllegalArgumentException If the number of buffers is less than 0.
     */
    init {
        if (buffers < 0) throw IllegalArgumentException("Buffers must be greater than or equal to 0")

        if (!pboSupported && buffers > 0)
            LOG.warn("Client tried to utilize PBOs, but they are not supported on the machine, falling back to direct buffer upload")

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
