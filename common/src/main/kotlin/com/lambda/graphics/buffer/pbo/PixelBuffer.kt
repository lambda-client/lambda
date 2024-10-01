package com.lambda.graphics.buffer.pbo

import com.lambda.graphics.buffer.BufferUsage
import com.lambda.threading.runGameScheduled
import org.lwjgl.opengl.GL45C.*
import java.nio.ByteBuffer
import kotlin.random.Random

/**
 * Represents a Pixel Buffer Object (PBO) that facilitates asynchronous data transfer to the GPU.
 * This class manages the creation, usage, and cleanup of PBOs and provides methods to upload and download data efficiently.
 *
 * - **Process**:
 * Every function that performs a pixel transfer operation can use buffer objects instead of client memory.
 * Functions that perform an upload operation, a pixel unpack, will use the buffer object bound to the target GL_PIXEL_UNPACK_BUFFER.
 * If a buffer is bound, then the pointer value that those functions take is not a pointer, but an offset from the beginning of that buffer.
 *
 * @property width The width of the buffer
 * @property height The height of the buffer
 * @property channels How many channels are present in the data
 * @property buffers The number of PBOs to be used. Default is 2, which allows double buffering.
 * @property bufferUsage The usage pattern of the buffer, indicating how the buffer will be used (static, dynamic, etc.).
 * @property init Code to run during the initialisation process.
 *
 * @see <a href="https://www.khronos.org/opengl/wiki/Pixel_Buffer_Object">Pixel Buffer Object</a>
 */
class PixelBuffer(
    private val width: Int,
    private val height: Int,
    private val channels: Int = 3,
    private val buffers: Int = 2,
    private val bufferUsage: BufferUsage = BufferUsage.STATIC,
    private val init: () -> Unit = {},
) {
    private val size = width * height * channels.toLong()
    private val pboIds = IntArray(buffers).apply { glGenBuffers(this) }
    private var index = 0

    /**
     * Uploads the given pixel data to the PBO and executes the provided processing function to manage the PBO's data transfer.
     *
     * This method binds the PBO, maps it to client memory, updates the pixel data, and unbinds the PBO.
     * If no data is provided, it fills the buffer with random bytes.
     *
     * @param data The [ByteBuffer] containing the pixel data to be uploaded. If null, the buffer is filled with random data.
     * @param transfer A function that takes an integer (the PBO ID) to manage the transfer process.
     * @return A [Throwable] if an error occurs during the upload process, or null if the operation is successful.
     */
    fun upload(
        data: ByteBuffer? = null,
        transfer: () -> Unit,
    ): Throwable? {
        // Bind PBO to unpack the data into whatever the transfer function does
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, pboIds[index])

        // Copy pixels to whatever this function does
        // Use offset instead of pointer
        transfer()

        // Swap buffer
        index = (index + 1) % buffers

        // Bind PBO to update pixel source
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, pboIds[index])

        // Map the buffer into the client's memory
        val mappedBuffer = glMapBufferRange(GL_PIXEL_UNPACK_BUFFER, 0, size, GL_MAP_WRITE_BIT)

        return if (mappedBuffer != null) {
            // Check if we're not mapping out of bounds
            if (mappedBuffer.capacity().toLong() != size)
                return IllegalStateException("The mapped buffer doesn't match the size! Mapping out of bounds?")

            // Update data directly on the mapped buffer
            if (data == null || data.limit() == 8) mappedBuffer.put(Random.nextBytes(width)) // Missingno
            else mappedBuffer.put(data)

            // Release the buffer
            if (!glUnmapBuffer(GL_PIXEL_UNPACK_BUFFER))
                return IllegalStateException("An unknown error occurred due to GPU memory availability.")

            // Unbind the PBO
            // Once bound with 0, all pixel operations behave normal ways.
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0)

            // No errors :)
            null
        } else IllegalStateException("Failed to map buffer, possibly due to insufficient virtual memory.")
    }

    /**
     * Cleans up resources by deleting the PBOs when the object is no longer in use.
     */
    protected fun finalize() {
        runGameScheduled {
            glDeleteBuffers(pboIds)
        }
    }

    /**
     * Initializes the PBOs, allocates memory for them, and handles unsupported PBO scenarios.
     *
     * @throws IllegalArgumentException If the number of buffers is less than 1.
     * @throws UnsupportedOperationException If the machine doesn't support PBOs.
     */
    init {
        init()

        // Fill the buffers with null data to allocate the memory spaces
        repeat(buffers) {
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, pboIds[it])
            glBufferData(GL_PIXEL_UNPACK_BUFFER, size, bufferUsage.gl)
        }

        // Unbind the buffer
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0)
    }
}
