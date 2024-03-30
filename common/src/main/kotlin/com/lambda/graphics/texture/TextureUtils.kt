package com.lambda.graphics.texture

import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.texture.NativeImage
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL13C.*
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.Transparency
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.roundToInt
import kotlin.math.sqrt

object TextureUtils {
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

    private fun readImage(bufferedImage: BufferedImage): Long {
        val stream = ByteArrayOutputStream()
        ImageIO.write(bufferedImage, "png", stream)

        val bytes = stream.toByteArray()
        val buffer = BufferUtils
            .createByteBuffer(bytes.size)
            .put(bytes)
            .flip()

        return NativeImage.read(buffer).pointer
    }

    fun getCharImage(font: Font, char: Char): BufferedImage? {
        if (!font.canDisplay(char)) return null

        val tempGraphics2D = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics()
        tempGraphics2D.font = font
        val fontMetrics = tempGraphics2D.fontMetrics
        tempGraphics2D.dispose()

        val charWidth = if (fontMetrics.charWidth(char) > 0) fontMetrics.charWidth(char) else 8
        val charHeight = if (fontMetrics.height > 0) fontMetrics.height else font.size

        val charImage = BufferedImage(charWidth, charHeight, BufferedImage.TYPE_INT_ARGB)
        val graphics2D = charImage.createGraphics()

        graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics2D.font = font
        graphics2D.color = Color.WHITE
        graphics2D.drawString(char.toString(), 0, fontMetrics.ascent)
        graphics2D.dispose()

        return charImage
    }

    fun BufferedImage.rescale(targetWidth: Int, targetHeight: Int): BufferedImage {
        val type = if (this.transparency == Transparency.OPAQUE)
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