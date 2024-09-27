package com.lambda.graphics.buffer.pbo

import com.lambda.Lambda.LOG
import com.lambda.graphics.buffer.BufferUsage
import com.lambda.graphics.texture.MipmapTexture
import com.lambda.graphics.texture.TextureUtils
import com.lambda.threading.runGameScheduled
import com.lambda.util.LambdaResource
import io.netty.buffer.ByteBuf
import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL45C.*
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import javax.imageio.ImageIO

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
    private val bufferUsage: BufferUsage = BufferUsage.STATIC,
    private val init: () -> Unit = {},
) {
    private val pboIds = IntArray(buffers).apply { glGenBuffers(this) }
    private val fences = LongArray(buffers) // Synchronization objects
    private var index = 0

    private val pboSupported = GL.getCapabilities().OpenGL15

    /**
     * Creates a new PBO use context required for everything related in this class.
     */
    fun use(block: PixelBuffer.() -> Unit) {
        if (buffers != 2) {
            index = 0
        }

        // Do the main stuff
        block()

        // Swap buffers
        index = (index + 1) % buffers
    }

    /**
     * Uploads the given pixel data to the PBO and executes the provided processing function to manage the PBO's data transfer.
     *
     * @param data The [ByteBuffer] containing the pixel data to be uploaded.
     */
    fun upload(data: ByteBuffer, transfer: () -> Unit = {}): Throwable? {
        // Wait for the previous PBO to finish if a fence exists
        if (fences[index] != 0L) {
            val ret = glClientWaitSync(fences[index], GL_SYNC_FLUSH_COMMANDS_BIT, 50000000) // 50 ms timeout
            if (ret == GL_ALREADY_SIGNALED || ret == GL_CONDITION_SATISFIED) {
                glDeleteSync(fences[index])
                fences[index] = 0L
            }
        }

        // Bind the current PBO for uploading
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, pboIds[index])

        println("Bind Buffer error ${glGetError()}")

        // Map the buffer into the client's memory
        val bufferData =
            glMapBufferRange(GL_PIXEL_UNPACK_BUFFER, 0, size, GL_MAP_WRITE_BIT or GL_MAP_INVALIDATE_BUFFER_BIT)

        println("Map Buffer error ${glGetError()}")

        return if (bufferData != null) {
            MemoryUtil.memCopy(data, bufferData)

            // Release the buffer
            if (!glUnmapBuffer(GL_PIXEL_UNPACK_BUFFER))
                return IllegalStateException("An unknown error occurred due to GPU memory availability.")
                    .also { LOG.error(it) }

            println("Unmap error ${glGetError()}")

            // Process
            transfer()

            println("Transfer error ${glGetError()}")

            // Insert a sync object to track when the GPU finishes reading from the PBO
            fences[index] = glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0)

            println("Fence error ${glGetError()}")

            // Unbind the PBO
            // Once bound with 0, all pixel operations behave normal ways.
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0)

            println("Unbind error ${glGetError()}")

            // No errors :)
            null
        } else IllegalStateException(
            """
            Failed to map the buffer\n
            There is most likely not enough virtual memory for the program to continue or
            """.trimIndent()
        )
    }

    /**
     * Downloads the data from the PBO from OpenGL back to the client memory
     */
    fun download(process: (ByteBuffer) -> Unit): Throwable? {
        // Bind the current PBO for writing
        glBindBuffer(GL_PIXEL_PACK_BUFFER, pboIds[index])

        // Map the buffer into the memory
        val bufferData = glMapBufferRange(GL_PIXEL_PACK_BUFFER, 0, size, GL_MAP_READ_BIT)
        return if (bufferData != null) {
            // Do something with the data
            process(bufferData)

            // Release the buffer
            glUnmapBuffer(GL_PIXEL_PACK_BUFFER)

            // Unbind the PBO
            glBindBuffer(GL_PIXEL_PACK_BUFFER, 0)

            // No errors :)
            null
        } else return IllegalStateException("Failed to map the buffer")
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
