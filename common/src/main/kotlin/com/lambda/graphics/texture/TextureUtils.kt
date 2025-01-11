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

    fun bindTexture(id: Int, slot: Int = 0) {
        RenderSystem.activeTexture(GL_TEXTURE0 + slot)
        RenderSystem.bindTexture(id)
    }

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

    fun readImage(
        image: ByteBuffer,
        format: NativeImage.Format,
    ) = NativeImage.read(format, image).pointer
}
