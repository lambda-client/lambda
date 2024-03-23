package com.lambda.graphics.renderer.gui.font.glyph

import com.lambda.graphics.texture.TextureUtils.getCharImage
import com.lambda.graphics.texture.TextureUtils.rescale
import com.lambda.util.math.Vec2d
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import kotlin.math.max
import kotlin.math.pow

class FontGlyphs(font: Font) {
    private val charMap = Int2ObjectOpenHashMap<CharInfo>()
    private val fontTexture: FontTexture

    var fontHeight = 0.0; private set

    init {
        val image = BufferedImage(TEXTURE_SIZE, TEXTURE_SIZE, BufferedImage.TYPE_INT_ARGB)

        val graphics = image.graphics as Graphics2D
        graphics.background = Color(0, 0, 0, 0)

        var x = SPACE
        var y = SPACE
        var rowHeight = 0

        charList.forEach { char ->
            val charImage = getCharImage(font, char)

            val fullWidth = charImage.width + SPACE
            val fullHeight = charImage.height + SPACE

            rowHeight = max(rowHeight, fullHeight)

            if (x + fullWidth >= TEXTURE_SIZE) {
                y += rowHeight
                x = SPACE
                rowHeight = 0
            }

            check(y + fullHeight <= TEXTURE_SIZE) { "Can't load font glyphs. Too small texture size" }

            graphics.drawImage(charImage, x, y, null)

            val size = Vec2d(charImage.width, charImage.height)
            val uv1 = Vec2d(x, y) * ONE_TEXEL_SIZE
            val uv2 = Vec2d(x, y).plus(size) * ONE_TEXEL_SIZE

            charMap[char.code] = CharInfo(size, uv1, uv2)
            fontHeight = max(fontHeight, size.y)

            x += charImage.width + SPACE
        }

        val lodImages = (0..LOD_LEVELS).map { level ->
            if (level == 0) return@map image
            val size = TEXTURE_SIZE / (2.0.pow(level).toInt())
            rescale(image, size)
        }

        fontTexture = FontTexture(lodImages)
    }

    fun bind() = fontTexture.bind()

    fun getChar(char: Char): CharInfo? =
        charMap[char.code]

    companion object {
        private const val TEXTURE_SIZE = 2048
        private const val SPACE = 2
        private const val ONE_TEXEL_SIZE = 1.0 / TEXTURE_SIZE
        private const val LOD_LEVELS = 4

        private val latin = ('a'..'z').toList()
        private val cyrillic = ('а'..'я').toList()

        private val german = "üÜöÖäÄß".toCharArray().toList()
        private val ukrainian = "ґҐїЇєЄ".toCharArray().toList()

        private val special = ((32..47) + (58..64) + (91..96) + (123..126)).map(::Char)

        private val charList = special +
                latin + cyrillic +
                german + ukrainian
    }
}