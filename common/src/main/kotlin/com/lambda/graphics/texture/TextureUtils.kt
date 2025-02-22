/*
 * Copyright 2024 Lambda
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

package com.lambda.graphics.texture

import com.mojang.blaze3d.systems.RenderSystem
import com.pngencoder.PngEncoder
import net.minecraft.client.texture.NativeImage
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL45C.*
import java.awt.image.BufferedImage
import java.nio.ByteBuffer

object TextureUtils {
    private const val COMPRESSION_LEVEL = 1
    private const val THREADED_COMPRESSION = false

    val encoderPreset = PngEncoder()
        .withCompressionLevel(COMPRESSION_LEVEL)
        .withMultiThreadedCompressionEnabled(THREADED_COMPRESSION)

    /**
     * Binds a texture to a specified texture slot.
     *
     * Activates the texture unit corresponding to GL_TEXTURE0 plus the slot offset, then binds the texture identified by the provided texture ID.
     *
     * @param id the identifier of the texture to bind.
     * @param slot the texture slot index (default is 0).
     */
    fun bindTexture(id: Int, slot: Int = 0) {
        RenderSystem.activeTexture(GL_TEXTURE0 + slot)
        RenderSystem.bindTexture(id)
    }

    /**
     * Configures the active texture's sampling and pixel storage parameters.
     *
     * This function sets the texture's minification and magnification filters using the provided values,
     * clamps the S and T texture coordinates to the edge, and resets the pixel unpacking parameters to their
     * default state. This ensures proper texture sampling and data alignment when uploading image data.
     *
     * @param minFilter the OpenGL filter to apply for texture minification.
     * @param magFilter the OpenGL filter to apply for texture magnification.
     */
    fun setupTexture(minFilter: Int, magFilter: Int) {
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, minFilter)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, magFilter)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)

        glPixelStorei(GL_UNPACK_ROW_LENGTH, 0)
        glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0)
        glPixelStorei(GL_UNPACK_SKIP_ROWS, 0)
        glPixelStorei(GL_UNPACK_ALIGNMENT, 4)
    }

    /**
     * Converts a BufferedImage to a native image pointer.
     *
     * This function encodes the given [bufferedImage] using a PNG encoder preset, transfers the encoded data into a ByteBuffer,
     * and then delegates to the ByteBuffer-based image reader with the specified [format]. It returns a pointer to the native image.
     *
     * @param bufferedImage the image to be converted.
     * @param format the target format for the native image.
     * @return a pointer to the native image.
     */
    fun readImage(
        bufferedImage: BufferedImage,
        format: NativeImage.Format,
    ): Long {
        val bytes = encoderPreset
            .withBufferedImage(bufferedImage)
            .toBytes()

        val buffer = BufferUtils
            .createByteBuffer(bytes.size)
            .put(bytes)
            .flip()

        return readImage(buffer, format)
    }

    /**
     * Reads image data from a ByteBuffer using a specified format and returns a pointer to the resulting native image.
     *
     * This function interprets the raw image data in the provided ByteBuffer according to the given format,
     * converts it into a NativeImage, and then returns the pointer to the native image data.
     *
     * @param image A ByteBuffer containing raw image data.
     * @param format The format used to decode the image data.
     * @return A pointer to the native image as a Long.
     */
    fun readImage(
        image: ByteBuffer,
        format: NativeImage.Format,
    ) = NativeImage.read(format, image).pointer
}
