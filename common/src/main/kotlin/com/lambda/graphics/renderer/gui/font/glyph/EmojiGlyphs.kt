package com.lambda.graphics.renderer.gui.font.glyph

import com.google.common.math.IntMath.pow
import com.lambda.Lambda.LOG
import com.lambda.graphics.texture.MipmapTexture
import com.lambda.http.Method
import com.lambda.http.request
import com.lambda.module.modules.client.RenderSettings
import com.lambda.util.math.Vec2d
import java.awt.Color
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.io.File
import java.util.zip.ZipFile
import javax.imageio.ImageIO
import kotlin.math.ceil
import kotlin.math.log2
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.days

class EmojiGlyphs(zipUrl: String) {
    private val emojiMap = mutableMapOf<String, GlyphInfo>()
    private lateinit var fontTexture: MipmapTexture

    private lateinit var image: BufferedImage
    private lateinit var graphics: Graphics2D

    init {
        runCatching {
            downloadAndProcessZip(zipUrl)
            LOG.info("Loaded ${emojiMap.size} emojis")
        }.onFailure {
            LOG.error("Failed to load emojis: ${it.message}", it)
            fontTexture = MipmapTexture(BufferedImage(1024, 1024, BufferedImage.TYPE_INT_ARGB))
        }
    }

    private fun downloadAndProcessZip(zipUrl: String) {
        val file = request(zipUrl) {
            method(Method.GET)
        }.maybeDownload("emojis.zip", maxAge = 30.days)

        fontTexture = MipmapTexture(processZip(file))
    }

    /**
     * Processes the given zip file and loads the emojis into the texture.
     *
     * @param file The zip file containing the emojis.
     * @return The texture containing the emojis.
     */
    private fun processZip(file: File): BufferedImage {
        ZipFile(file).use { zip ->
            val firstImage = ImageIO.read(zip.getInputStream(zip.entries().nextElement()))
            val length = zip.size().toDouble()

            val textureDimensionLength: (Int) -> Int = { dimLength ->
                pow(2, ceil(log2((dimLength + STEP) * sqrt(length))).toInt())
            }

            val width = textureDimensionLength(firstImage.width)
            val height = textureDimensionLength(firstImage.height)
            val texelSize = Vec2d.ONE / Vec2d(width, height)

            image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            graphics = image.graphics as Graphics2D
            graphics.color = Color(0, 0, 0, 0)

            var x = 0
            var y = 0

            for (entry in zip.entries()) {
                val name = entry.name.substringAfterLast("/").substringBeforeLast(".")
                val emoji = ImageIO.read(zip.getInputStream(entry))

                if (x + emoji.width >= image.width) {
                    y += emoji.height + STEP
                    x = 0
                }

                check(y + emoji.height < image.height) { "Can't load emoji glyphs. Texture size is too small" }

                graphics.drawImage(emoji, x, y, null)

                val size = Vec2d(emoji.width, emoji.height)
                val uv1 = Vec2d(x, y) * texelSize
                val uv2 = Vec2d(x, y).plus(size) * texelSize

                emojiMap[name] = GlyphInfo(size, -uv1, -uv2)

                x += emoji.width + STEP
            }
        }

        return image
    }

    fun bind() {
        with(fontTexture) {
            bind(GL_TEXTURE_SLOT)
            setLOD(RenderSettings.lodBias.toFloat())
        }
    }

    fun emojiFromString(emoji: String) = emojiMap[emoji]

    companion object {
        private const val STEP = 2
        private const val GL_TEXTURE_SLOT = 1 // TODO: Texture slot borrowing
    }
}
