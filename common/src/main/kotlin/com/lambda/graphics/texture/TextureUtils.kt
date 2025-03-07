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
import java.awt.*
import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import kotlin.math.roundToInt
import kotlin.math.sqrt

object TextureUtils {
    private const val COMPRESSION_LEVEL = 1
    private const val THREADED_COMPRESSION = false

    private val metricCache = mutableMapOf<Font, FontMetrics>()

    val encoderPreset = PngEncoder()
        .withCompressionLevel(COMPRESSION_LEVEL)
        .withMultiThreadedCompressionEnabled(THREADED_COMPRESSION)

    fun bindTexture(id: Int, slot: Int = 0) {
        RenderSystem.activeTexture(GL_TEXTURE0 + slot)
        RenderSystem.bindTexture(id)
    }

    fun upload(bufferedImage: BufferedImage, lod: Int) {
        val width = bufferedImage.width
        val height = bufferedImage.height

        glTexImage2D(GL_TEXTURE_2D, lod, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, readImage(bufferedImage))

        setupTexture(GL_LINEAR_MIPMAP_LINEAR, GL_LINEAR)
    }

    fun setupLOD(levels: Int) {
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_LOD, 0)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LOD, levels)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_BASE_LEVEL, 0)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, levels)
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
        bytes: ByteArray,
        format: NativeImage.Format = NativeImage.Format.RGBA,
    ): NativeImage {
        val buffer = BufferUtils
            .createByteBuffer(bytes.size)
            .put(bytes)
            .flip()

        return NativeImage.read(format, buffer)
    }

    fun readImage(
        bufferedImage: BufferedImage,
        format: NativeImage.Format = NativeImage.Format.RGBA,
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
        format: NativeImage.Format = NativeImage.Format.RGBA,
    ) = NativeImage.read(format, image).pointer

    fun getCharImage(font: Font, codePoint: Char): BufferedImage? {
        if (!font.canDisplay(codePoint)) return null

        val fontMetrics = metricCache.getOrPut(font) {
            val image = BufferedImage(COMPRESSION_LEVEL, COMPRESSION_LEVEL, BufferedImage.TYPE_INT_ARGB)
            val graphics2D = image.createGraphics()

            graphics2D.font = font
            graphics2D.dispose()

            image.graphics.getFontMetrics(font)
        }

        val charWidth = if (fontMetrics.charWidth(codePoint) > 0) fontMetrics.charWidth(codePoint) else 8
        val charHeight = if (fontMetrics.height > 0) fontMetrics.height else font.size

        val charImage = BufferedImage(charWidth, charHeight, BufferedImage.TYPE_INT_ARGB)
        val graphics2D = charImage.createGraphics()

        graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics2D.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_DEFAULT)

        graphics2D.font = font
        graphics2D.color = Color.WHITE
        graphics2D.drawString(codePoint.toString(), 0, fontMetrics.ascent)
        graphics2D.dispose()

        return charImage
    }

    fun BufferedImage.rescale(targetWidth: Int, targetHeight: Int): BufferedImage {
        val type = if (transparency == Transparency.OPAQUE)
            BufferedImage.TYPE_INT_RGB
        else BufferedImage.TYPE_INT_ARGB

        var image = this

        var width = image.width
        var height = image.height

        val divisorX = sqrt((width / targetWidth).toDouble())
        val divisorY = sqrt((height / targetHeight).toDouble())

        do {
            if (width > targetWidth) {
                width = (width / divisorX).roundToInt().coerceAtLeast(targetWidth)
            }

            if (height > targetHeight) {
                height = (height / divisorY).roundToInt().coerceAtLeast(targetHeight)
            }

            val tempImage = BufferedImage(width, height, type)
            val graphics2D = tempImage.createGraphics()

            graphics2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            graphics2D.drawImage(image, 0, 0, width, height, null)
            graphics2D.dispose()

            image = tempImage
        } while (width != targetWidth || height != targetHeight)

        return image
    }
}
