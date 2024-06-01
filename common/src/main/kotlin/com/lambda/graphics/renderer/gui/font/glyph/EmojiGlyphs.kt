package com.lambda.graphics.renderer.gui.font.glyph

import com.lambda.graphics.texture.MipmapTexture
import com.lambda.module.modules.client.FontSettings
import com.lambda.util.math.Vec2d
import java.awt.Color
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.net.URL
import java.nio.file.Files
import java.util.zip.ZipFile
import javax.imageio.ImageIO
import kotlin.math.ceil
import kotlin.math.sqrt

class EmojiGlyphs(zipUrl: String) {
    private val emojiMap = mutableMapOf<String, CharInfo>()
    private val fontTexture: MipmapTexture

    init {
        val file = Files.createTempFile("emoji", ".zip").toFile()
        val url = URL(zipUrl)
        file.writeBytes(url.readBytes())

        ZipFile(file).use { zip ->
            val size = zip.entries().asSequence().count()
            val dimensions = Vec2d(72.0, 72.0)
            val texelSize = 1.0 / size
            val width = 72 * ceil(sqrt(size.toDouble())).toInt()

            val image = BufferedImage(width, width, BufferedImage.TYPE_INT_ARGB)
            val graphics = image.graphics as Graphics2D
            graphics.background = Color(0, 0, 0, 0)

            var x = 0
            var y = 0

            zip.entries().asSequence().forEach { entry ->
                val name = entry.name.substringAfterLast("/").substringBeforeLast(".")
                val charImage = ImageIO.read(zip.getInputStream(entry))

                if (x + 72 >= width) {
                    y += 72
                    x = 0
                }

                graphics.drawImage(charImage, x, y, null)

                val uv1 = Vec2d(x.toDouble(), y.toDouble()) * texelSize
                val uv2 = Vec2d(x, y).plus(dimensions) * texelSize
                emojiMap[name] = CharInfo(dimensions, uv1, uv2)

                x += 72
            }

            fontTexture = MipmapTexture(image)
        }
    }

    fun bind() {
        with(fontTexture) {
            bind()
            setLOD(FontSettings.lodBias.toFloat())
        }
    }

    fun getEmoji(emoji: String): CharInfo? =
        emojiMap[emoji]
}
