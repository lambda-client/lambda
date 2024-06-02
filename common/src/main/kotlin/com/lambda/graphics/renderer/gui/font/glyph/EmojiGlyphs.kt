package com.lambda.graphics.renderer.gui.font.glyph

import com.lambda.Lambda.LOG
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
import kotlin.system.measureTimeMillis

class EmojiGlyphs(zipUrl: String) {
    private val emojiMap = mutableMapOf<String, CharInfo>()
    private val fontTexture: MipmapTexture

    init {
        val file = Files.createTempFile("emoji", ".zip").toFile()
        val url = URL(zipUrl)
        file.writeBytes(url.readBytes())

        ZipFile(file).use { zip ->
            // someone please refactor this
            val first = ImageIO.read(zip.getInputStream(zip.entries().nextElement()))

            val size = zip.entries().asSequence().count()
            val dimensions = Vec2d(first.width.toDouble(), first.height.toDouble())
            val texelSize = 1.0 / size
            val width = first.width * ceil(sqrt(size.toDouble())).toInt()
            val height = first.height * ceil(sqrt(size.toDouble())).toInt()

            val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            val graphics = image.graphics as Graphics2D
            graphics.background = Color(0, 0, 0, 0)

            var x = 0
            var y = 0

            val time = measureTimeMillis {
                zip.entries().asSequence().forEach { entry ->
                    val name = entry.name.substringAfterLast("/").substringBeforeLast(".")
                    val emoji = ImageIO.read(zip.getInputStream(entry))

                    if (x + emoji.width >= width) {
                        y += emoji.height
                        x = 0
                    }

                    graphics.drawImage(emoji, x, y, null)

                    val uv1 = Vec2d(x.toDouble(), y.toDouble()) * texelSize
                    val uv2 = Vec2d(x, y).plus(dimensions) * texelSize
                    emojiMap[name] = CharInfo(dimensions, uv1 * -1.0, uv2 * -1.0)

                    x += emoji.width
                }
            }

            fontTexture = MipmapTexture(image)

            LOG.info("Loaded $size emojis in $time ms")
        }
    }

    fun bind() {
        with(fontTexture) {
            bind(GL_TEXTURE_SLOT)
            setLOD(FontSettings.lodBias.toFloat())
        }
    }

    fun getEmoji(emoji: String): CharInfo? =
        emojiMap[emoji]

    companion object {
        private const val GL_TEXTURE_SLOT = 1
    }
}
