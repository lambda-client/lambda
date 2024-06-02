package com.lambda.graphics.renderer.gui.font.glyph

import com.google.common.math.IntMath.pow
import com.lambda.Lambda.LOG
import com.lambda.graphics.texture.MipmapTexture
import com.lambda.module.modules.client.FontSettings
import com.lambda.util.math.Vec2d
import java.awt.Color
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.io.File
import java.net.URL
import java.util.zip.ZipFile
import javax.imageio.ImageIO
import kotlin.math.ceil
import kotlin.math.log2
import kotlin.math.sqrt
import kotlin.system.measureTimeMillis

class EmojiGlyphs(zipUrl: String) {
    private val emojiMap = mutableMapOf<String, CharInfo>()
    private val fontTexture: MipmapTexture

    private val image: BufferedImage
    private val graphics: Graphics2D

    init {
        var x = 0
        var y = 0

        val time = measureTimeMillis {
            val file = File.createTempFile("emoji", ".zip")
            file.deleteOnExit()

            file.outputStream().use { output ->
                URL(zipUrl).openStream().use { input ->
                    input.copyTo(output)
                }
            }

            ZipFile(file).use { zip ->
                val firstImage = ImageIO.read(zip.getInputStream(zip.entries().nextElement()))

                val length = zip.size().toDouble()

                // TODO: This is a hack but it works
                val width = pow(2, ceil(log2(firstImage.width * sqrt(length))).toInt())
                val height = pow(2, ceil(log2(firstImage.height * sqrt(length))).toInt())
                val texelSize = 1.0 / width // This assumes that the texture is a power of 2

                image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
                graphics = image.graphics as Graphics2D
                graphics.color = Color(0, 0, 0, 0)

                for (entry in zip.entries()) {
                    val name = entry.name.substringAfterLast("/").substringBeforeLast(".")
                    val emoji = ImageIO.read(zip.getInputStream(entry))

                    if (x + emoji.width >= image.width) {
                        y += emoji.height
                        x = 0
                    }

                    check(y + emoji.height < image.height) { "Can't load emoji glyphs. Texture size is too small" }

                    graphics.drawImage(emoji, x, y, null)

                    val size = Vec2d(emoji.width, emoji.height)
                    val uv1 = Vec2d(x, y) * texelSize
                    val uv2 = Vec2d(x, y).plus(size) * texelSize

                    emojiMap[name] = CharInfo(size, -uv1, -uv2)

                    x += emoji.width
                }
            }

            ImageIO.write(image, "png", File("emoji.png"))

            fontTexture = MipmapTexture(image)
        }

        LOG.info("Loaded ${emojiMap.size} emojis in $time ms")
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
        private const val GL_TEXTURE_SLOT = 1 // TODO: Texture slot borrowing
    }
}
