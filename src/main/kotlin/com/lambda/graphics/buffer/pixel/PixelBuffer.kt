/*
 * Copyright 2025 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.graphics.buffer.pixel

import com.lambda.graphics.buffer.Buffer
import com.lambda.graphics.texture.Texture
import com.lambda.util.math.MathUtils.toInt
import org.lwjgl.opengl.GL45C.*
import java.nio.ByteBuffer

/**
 * Represents a Pixel Buffer Object (PBO) that facilitates asynchronous data transfer to the GPU.
 *
 * Every function that performs a pixel transfer operation can use buffer objects instead of client memory.
 * Functions that perform an upload operation, a pixel unpack, will use the buffer object bound to the target GL_PIXEL_UNPACK_BUFFER.
 * If a buffer is bound, then the pointer value that those functions take is not a pointer, but an offset from the beginning of that buffer.
 *
 * Asynchronous should only be used for medium-size blocks of data being updated one time or less per frame
 * Persistent should only be used for streaming operations with large blocks of data updated once or more per frame
 *
 * @property texture        The [Texture] instance to use
 * @property asynchronous   Whether to use 2 buffers or not
 * @property persistent     Whether to map a block in memory to upload or not
 *
 * @see <a href="https://www.khronos.org/opengl/wiki/Pixel_Buffer_Object">Pixel buffer object</a>
 */
class PixelBuffer(
    private val texture: Texture,
    private val asynchronous: Boolean = false,
    private val persistent: Boolean = false,
) : Buffer(buffers = asynchronous.toInt() + 1) {
    override val usage = GL_STATIC_DRAW
    override val target = GL_PIXEL_UNPACK_BUFFER
    override val access =
        if (persistent) GL_MAP_WRITE_BIT or GL_DYNAMIC_STORAGE_BIT or GL_MAP_PERSISTENT_BIT or GL_MAP_COHERENT_BIT
        else GL_MAP_WRITE_BIT or GL_DYNAMIC_STORAGE_BIT

    private val channels = channelMapping[texture.format] ?: throw IllegalArgumentException("Invalid image format, expected OpenGL format, got ${texture.format} instead")
    private val size = texture.width * texture.height * channels * 1L
    //    private var sharedRegion: ByteBuffer? = null

    override fun upload(data: ByteBuffer, offset: Long) {
        if (!asynchronous) {
            glBindTexture(GL_TEXTURE_2D, texture.id)
            glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, texture.width, texture.height, texture.format, GL_UNSIGNED_BYTE, data)
            glBindTexture(GL_TEXTURE_2D, 0)
            return
        }

        bind()
        glBindTexture(GL_TEXTURE_2D, texture.id)

        // Copy pixels from PBO to texture object
        // Use offset instead of pointer
        glTexSubImage2D(
            GL_TEXTURE_2D,        // Target
            0,               // Mipmap level
            0, 0,    // x and y offset
            texture.width,        // Width of the texture
            texture.height,       // Height of the texture
            texture.format,       // Format of your texture (depends on your data)
            GL_UNSIGNED_BYTE,     // Type (depends on your data)
            0,              // PBO offset (for asynchronous transfer)
        )

        swap()
        bind()

        //        if (persistent) data.putTo(sharedRegion)
        //        else update(data, offset)
        update(data, offset)

        bind(0)
    }

    init {
        if (!texture.initialized) throw IllegalStateException("Cannot use uninitialized textures for pixel buffers")

        // We can't call the texture's bind method because the animated texture updates the
        // data when binding the texture, causing a null pointer exception due to the animated
        // texture object not being initialized
        glBindTexture(GL_TEXTURE_2D, texture.id)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, texture.width, texture.height, 0, texture.format, GL_UNSIGNED_BYTE, 0)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)

        storage(size)

        //        bind()
        //        sharedRegion = if (persistent) map(size, 0) else null
        //        bind(0)
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
    }
}
