package com.lambda.graphics.buffer.pixel

import com.lambda.graphics.buffer.IBuffer
import com.lambda.graphics.gl.padding
import com.lambda.graphics.gl.putTo
import com.lambda.graphics.texture.Texture
import org.lwjgl.opengl.GL45C.*
import java.nio.ByteBuffer

/**
 * Represents a Pixel Buffer Object (PBO) that facilitates asynchronous data transfer to the GPU.
 * This class manages the creation, usage, and cleanup of PBOs and provides methods to upload (map) data efficiently.
 *
 * **Process**:
 * Every function that performs a pixel transfer operation can use buffer objects instead of client memory.
 * Functions that perform an upload operation, a pixel unpack, will use the buffer object bound to the target GL_PIXEL_UNPACK_BUFFER.
 * If a buffer is bound, then the pointer value that those functions take is not a pointer, but an offset from the beginning of that buffer.
 *
 * @property width      The width of the texture
 * @property height     The height of the texture
 * @property texture    The [Texture] instance
 * @property format     The image format that will be uploaded
 *
 * @see <a href="https://www.khronos.org/opengl/wiki/Pixel_Buffer_Object">Pixel Buffer Object</a>
 */
class PixelBuffer(
    private val width: Int,
    private val height: Int,
    private val texture: Texture,
    private val format: Int,
) : IBuffer {
    override val buffers: Int = 2
    override val usage: Int = GL_STATIC_DRAW
    override val target: Int = GL_PIXEL_UNPACK_BUFFER
    override val access: Int = GL_MAP_WRITE_BIT or GL_MAP_COHERENT_BIT
    override var index = 0
    override val bufferIds = IntArray(buffers).apply { glGenBuffers(this) }

    private val channels = channelMapping[format] ?: throw IllegalArgumentException("Image format unsupported")
    private val internalFormat = reverseChannelMapping[channels] ?: throw IllegalArgumentException("Image internal format unsupported")
    private val size = width * height * channels * 1L

    override fun upload(
        data: ByteBuffer,
        offset: Long,
    ): Throwable? {
        // Bind PBO to unpack the data into the texture
        bind()

        // Bind the texture and PBO
        glBindTexture(GL_TEXTURE_2D, texture.id)

        // Copy pixels from PBO to texture object
        // Use offset instead of pointer
        glTexSubImage2D(
            GL_TEXTURE_2D,        // Target
            0,                    // Mipmap level
            0, 0,                 // x and y offset
            width, height,        // width and height of the texture (set to your size)
            format,               // Format (depends on your data)
            GL_UNSIGNED_BYTE,     // Type (depends on your data)
            0,                    // PBO offset (for asynchronous transfer)
        )

        // Unbind the texture
        glBindTexture(GL_TEXTURE_2D, 0)

        // Swap the buffer
        swap()

        // Bind PBO to update pixel source
        bind()

        // Map the buffer into the client's memory
        val error = map(offset, size, data::putTo)

        // Unbind
        bind(0)

        return error
    }

    init {
        // Bind the texture
        glBindTexture(GL_TEXTURE_2D, texture.id)

        // Calculate memory padding in the case we are using tightly
        // packed data in order to save memory and satisfy the computer's
        // architecture memory alignment
        // https://en.wikipedia.org/wiki/Data_structure_alignment
        // In this case we calculate the padding and subtract this to 4
        // in order to tell the padding size
        glPixelStorei(GL_UNPACK_ALIGNMENT, 4 - padding(channels))

        // Allocate texture storage
        // TODO: Might want to figure out the data type based on the input
        glTexImage2D(GL_TEXTURE_2D, 0, internalFormat, width, height, 0, format, GL_UNSIGNED_BYTE, 0)

        // Set the texture parameters
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)

        // Unbind the texture
        glBindTexture(GL_TEXTURE_2D, 0)

        // Fill the buffers with null data to allocate the memory spaces
        grow(size)
    }

    companion object {
        /**
         * Returns how many channels are used for each image format
         */
        private val channelMapping = mapOf(
            GL_RED to 1,
            GL_GREEN to 1,
            GL_BLUE to 1,
            GL_ALPHA to 1,
            GL_RG to 2,
            GL_RGB to 3,
            GL_BGR to 3,
            GL_RGBA to 4,
            GL_BGRA to 4,
        )

        /**
         * Returns an internal format based on how many channels there are
         */
        private val reverseChannelMapping = mapOf(
            1 to GL_RED,
            2 to GL_RG,
            3 to GL_RGB,
            4 to GL_RGBA,
        )
    }
}
