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

package com.lambda.graphics.renderer.gui.font.core

import com.google.common.math.IntMath
import com.lambda.core.Loadable
import com.lambda.graphics.texture.TextureOwner.uploadField
import com.lambda.http.Method
import com.lambda.http.request
import com.lambda.threading.runGameScheduled
import com.lambda.util.math.Vec2d
import com.lambda.util.stream
import it.unimi.dsi.fastutil.objects.Object2DoubleArrayMap
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import java.awt.*
import java.awt.image.BufferedImage
import java.util.zip.ZipFile
import javax.imageio.ImageIO
import kotlin.math.ceil
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.days

/**
 * The [LambdaAtlas] manages the creation and binding of texture atlases for fonts, emojis and user defined atlases
 * It stores glyph information, manages texture uploads, and provides functionality to build texture buffers for fonts and emoji sets
 *
 * It caches font information and emoji data for efficient rendering and includes mechanisms for uploading and binding texture atlases
 *
 * It's also possible to upload custom atlases and bind them with no hassle
 * ```kt
 * enum class ExampleFont {
 *     CoolFont("Cool-Font");
 * }
 *
 * fun loadFont(...) = BufferedImage
 *
 * // Function extension from [TexturePipeline]
 * ExampleFont.CoolFont.upload(loadFont(...)) // The extension keeps a reference to the font owner
 *
 * ...
 *
 * onRender {
 *     ExampleFont.CoolFont.bind()
 * }
 * ```
 */
object LambdaAtlas : Loadable {
    private val fontMap = mutableMapOf<Any, Map<Char, GlyphInfo>>()
    private val emojiMap = mutableMapOf<Any, Map<String, GlyphInfo>>()

    private val bufferPool =
        mutableMapOf<Any, BufferedImage>() // This array is nuked once the data is dispatched to OpenGL

    private val fontCache = mutableMapOf<Any, Font>()
    private val metricCache = mutableMapOf<Font, FontMetrics>()
    private val heightCache = Object2DoubleArrayMap<Font>()

    operator fun LambdaFont.get(char: Char): GlyphInfo? = fontMap.getValue(this)[char]
    operator fun LambdaEmoji.get(string: String): GlyphInfo? = emojiMap.getValue(this)[string.removeSurrounding(":")]

    val LambdaFont.height: Double
        get() = heightCache.getDouble(fontCache[this@height])

    val LambdaEmoji.keys
        get() = emojiMap.getValue(this)

    const val CHAR_SPACE = 8

    /**
     * Builds the buffer for an emoji set by reading a ZIP file containing emoji images.
     * The images are arranged into a texture atlas, and their UV coordinates are computed for later rendering.
     *
     * @throws IllegalStateException If the texture size is too small to fit the emojis.
     */
    fun LambdaEmoji.buildBuffer() {
        val file = request(url) {
            method(Method.GET)
        }.maybeDownload("emojis.zip", maxAge = 30.days)

        var image: BufferedImage

        ZipFile(file).use { zip ->
            val firstImage = ImageIO.read(zip.getInputStream(zip.entries().nextElement()))
            val length = zip.size().toDouble()

            val textureDimensionLength: (Int) -> Int = { dimLength ->
                IntMath.pow(2, ceil(log2((dimLength + CHAR_SPACE) * sqrt(length))).toInt())
            }

            val width = textureDimensionLength(firstImage.width)
            val height = textureDimensionLength(firstImage.height)
            val texelSize = Vec2d.ONE / Vec2d(width, height)

            image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            val graphics = image.graphics as Graphics2D
            graphics.color = Color(0, 0, 0, 0)

            var x = CHAR_SPACE
            var y = CHAR_SPACE

            val constructed = Object2ObjectOpenHashMap<String, GlyphInfo>()
            for (entry in zip.entries()) {
                val name = entry.name.substringAfterLast("/").substringBeforeLast(".")
                val emoji = ImageIO.read(zip.getInputStream(entry))

                val charWidth = emoji.width + CHAR_SPACE
                val charHeight = emoji.height + CHAR_SPACE

                if (x + charWidth >= image.width) {
                    check(y + charHeight < image.height) { "Can't load emoji glyphs. Texture size is too small" }

                    y += charHeight
                    x = 0
                }

                graphics.drawImage(emoji, x, y, null)

                val size = Vec2d(emoji.width, emoji.height)
                val uv1 = Vec2d(x, y) * texelSize
                val uv2 = Vec2d(x, y).plus(size) * texelSize

                constructed[name] = GlyphInfo(size, -uv1, -uv2)

                x += emoji.width + 2
            }

            emojiMap[this@buildBuffer] = constructed
        }

        bufferPool[this@buildBuffer] = image
    }

    fun LambdaFont.buildBuffer(
        characters: Int = 2048 // How many characters from that font should be used for the generation
    ) {
        val font = fontCache.computeIfAbsent(this) {
            Font.createFont(Font.TRUETYPE_FONT, "fonts/$fontName.ttf".stream).deriveFont(128.0f)
        }

        val textureSize = characters * 2
        val oneTexelSize = 1.0 / textureSize

        val image = BufferedImage(textureSize, textureSize, BufferedImage.TYPE_INT_ARGB)

        val graphics = image.graphics as Graphics2D
        graphics.background = Color(0, 0, 0, 0)

        var x = CHAR_SPACE
        var y = CHAR_SPACE
        var rowHeight = 0

        val constructed = mutableMapOf<Char, GlyphInfo>()
        (Char.MIN_VALUE..<characters.toChar()).forEach { char ->
            val charImage = getCharImage(font, char) ?: return@forEach

            rowHeight = max(rowHeight, charImage.height + CHAR_SPACE)
            val charWidth = charImage.width + CHAR_SPACE

            if (x + charWidth >= textureSize) {
                // Check if possible to step to the next row
                check(y + rowHeight <= textureSize) { "Can't load font glyphs. Texture size is too small" }

                y += rowHeight
                x = 0
                rowHeight = 0
            }

            graphics.drawImage(charImage, x, y, null)

            val size = Vec2d(charImage.width, charImage.height)
            val uv1 = Vec2d(x, y) * oneTexelSize
            val uv2 = Vec2d(x, y).plus(size) * oneTexelSize

            constructed[char] = GlyphInfo(size, uv1, uv2)
            heightCache[font] = max(heightCache.getDouble(font), size.y) // No compare set unfortunately

            x += charWidth
        }

        fontMap[this@buildBuffer] = constructed
        bufferPool[this@buildBuffer] = image
    }

    // TODO: Change this when we've refactored the loadables
    override fun load(): String {
        LambdaFont.entries.forEach(LambdaFont::load)
        LambdaEmoji.entries.forEach(LambdaEmoji::load)

        val str = "Loaded ${bufferPool.size} fonts" // avoid race condition

        runGameScheduled {
            bufferPool.forEach { (owner, image) -> owner.uploadField(image) }
            bufferPool.clear()
        }

        return str
    }

    private fun getCharImage(font: Font, codePoint: Char): BufferedImage? {
        if (!font.canDisplay(codePoint)) return null

        val fontMetrics = metricCache.getOrPut(font) {
            val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
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
}
