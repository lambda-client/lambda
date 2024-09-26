package com.lambda.graphics.buffer.pbo

import com.lambda.graphics.buffer.BufferUsage
import com.lambda.threading.runGameScheduled
import net.minecraft.block.entity.HopperBlockEntity.transfer
import net.minecraft.structure.StructureTemplate.process
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL45C.*
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.libc.LibCString.memcpy
import java.nio.ByteBuffer

/**
 * Represents a Pixel Buffer Object (PBO) that facilitates asynchronous data transfer to the GPU.
 * This class manages the creation, usage, and cleanup of PBOs and provides methods to upload and download data efficiently.
 *
 * - **Process**:
 * Every function that performs a pixel transfer operation can use buffer objects instead of client memory.
 * Functions that perform an upload operation, a pixel unpack, will use the buffer object bound to the target GL_PIXEL_UNPACK_BUFFER.
 * Functions that perform a download operation, a pixel pack, will use the buffer object bound to the GL_PIXEL_PACK_BUFFER.
 * These functions only use buffer objects if one is bound to that particular binding point when the function is called.
 * If a buffer is bound, then the pointer value that those functions take is not a pointer, but an offset from the beginning of that buffer.
 *
 * @property size The size of the buffer(s).
 * @property buffers The number of PBOs to be used. Default is 2, which allows double buffering.
 * @property bufferUsage The usage pattern of the buffer, indicating how the buffer will be used (static, dynamic, etc.).
 * @property init Code to run during the initialisation process.
 *
 * @see <a href="https://www.khronos.org/opengl/wiki/Pixel_Buffer_Object">Pixel Buffer Object</a>
 */
class PixelBuffer(
    private val size: Long,
    private val buffers: Int = 2,
    private val bufferUsage: BufferUsage = BufferUsage.DYNAMIC,
    private val init: () -> Unit = {},
) {
    private val pboIds = IntArray(buffers).apply { glGenBuffers(this) }
    private var writeIdx = 0 // Buffer being filled by the CPU
    private var uploadIdx = 0 // Buffer to transfer data to the GPU

    private val pboSupported = GL.getCapabilities().OpenGL15 || GL.getCapabilities().GL_ARB_pixel_buffer_object

    private val queries = IntArray(2).apply { glGenQueries(this) }
    private var transferRate = 0L // The transfer rate in bytes per second
    private val uploadTime get() = IntArray(1).apply { glGetQueryObjectiv(queries[0], GL_QUERY_RESULT, this) }.first()
    private val downloadTime get() = IntArray(1).apply { glGetQueryObjectiv(queries[1], GL_QUERY_RESULT, this) }.first()

    /**
     * Creates a new PBO use context required for everything related in this class.
     */
    fun use(block: PixelBuffer.() -> Unit) {
        if (buffers >= 2)
            uploadIdx = (writeIdx + 1) % buffers

        // Do the main stuff
        block()

        // Swap the indices
        writeIdx = uploadIdx
    }

    /**
     * Uploads the given pixel data to the PBO and executes the provided processing function to manage the PBO's data transfer.
     *
     * @param data The [ByteBuffer] containing the pixel data to be uploaded.
     */
    fun upload(data: ByteBuffer, transfer: () -> Unit = {}) =
        recordTransfer(queries[0]) {
            // Bind the current PBO for uploading
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, pboIds[uploadIdx])

            // Map the buffer into the memory
            val bufferData = glMapBuffer(GL_PIXEL_UNPACK_BUFFER, GL_WRITE_ONLY)
            if (bufferData != null) {
                MemoryUtil.memCopy(data, bufferData)

                // Release the buffer
                glUnmapBuffer(GL_PIXEL_UNPACK_BUFFER)
            } else throw IllegalStateException("Failed to map the buffer")

            // Process
            transfer()

            // Unbind the PBO
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0)
        }

    /**
     * Downloads the data from the PBO from OpenGL back to the client memory
     */
    fun download(process: (ByteBuffer) -> Unit) =
        recordTransfer(queries[1]) {
            // Bind the current PBO for writing
            glBindBuffer(GL_PIXEL_PACK_BUFFER, pboIds[writeIdx])

            // Map the buffer into the memory
            val bufferData = glMapBuffer(GL_PIXEL_PACK_BUFFER, GL_READ_ONLY, size, null)
            if (bufferData != null) {
                // Do something with the data
                process(bufferData)

                // Release the buffer
                glUnmapBuffer(GL_PIXEL_PACK_BUFFER)
            } else throw IllegalStateException("Failed to map the buffer")

            // Unbind the PBO
            glBindBuffer(GL_PIXEL_PACK_BUFFER, 0)
        }

    /**
     * Measures and records the time taken to transfer data to the PBO, calculating the transfer rate in bytes per second.
     *
     * @param block A lambda function representing the block of code where the transfer occurs.
     */
    private fun recordTransfer(query: Int, block: () -> Unit) {
        // Start the timer
        glBeginQuery(GL_TIME_ELAPSED, query)

        // Perform the transfer
        block()

        // Stop the timer
        glEndQuery(GL_TIME_ELAPSED)

        // Calculate the transfer rate
        val time = if (query == 0) uploadTime else downloadTime
        if (time > 0) transferRate = (size * 1_000_000_000) / time
    }

    /**
     * Cleans up resources by deleting the PBOs when the object is no longer in use.
     */
    protected fun finalize() {
        runGameScheduled {
            glDeleteBuffers(pboIds)
            glDeleteQueries(queries)
        }
    }

    /**
     * Initializes the PBOs, allocates memory for them, and handles unsupported PBO scenarios.
     *
     * @throws IllegalArgumentException If the number of buffers is less than 1.
     * @throws UnsupportedOperationException If the machine doesn't support PBOs.
     */
    init {
        if (buffers < 1) throw IllegalArgumentException("Buffers must be greater than 0")

        if (!pboSupported)
            throw UnsupportedOperationException("Client tried to utilize PBOs, but they are not supported on the machine.")

        init()

        // Fill the buffers with null data to allocate the memory spaces
        repeat(buffers) {
            glBindBuffer(GL_PIXEL_PACK_BUFFER, pboIds[it])
            glBufferData(GL_PIXEL_PACK_BUFFER, size, bufferUsage.gl)
        }

        glBindBuffer(GL_PIXEL_PACK_BUFFER, 0) // Unbind the buffer
    }
}
