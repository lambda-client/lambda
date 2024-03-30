package com.lambda.graphics.renderer.gui.font.glyph

import com.lambda.Lambda
import com.lambda.graphics.texture.MipmapTexture
import com.lambda.graphics.texture.TextureUtils.getCharImage
import com.lambda.module.modules.client.FontSettings
import com.lambda.util.math.Vec2d
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import kotlin.math.max
import kotlin.system.measureTimeMillis

class FontGlyphs(font: Font) {
    private val charMap = Int2ObjectOpenHashMap<CharInfo>()
    private val fontTexture: MipmapTexture

    var fontHeight = 0.0; private set

    init {
        val time = measureTimeMillis {
            val image = BufferedImage(TEXTURE_SIZE, TEXTURE_SIZE, BufferedImage.TYPE_INT_ARGB)

            val graphics = image.graphics as Graphics2D
            graphics.background = Color(0, 0, 0, 0)

            var x = 0
            var y = 0
            var rowHeight = 0

            // Because UTF16 takes 2 bytes per character, we can't use the full range of characters
            (Char.MIN_VALUE..<TEXTURE_SIZE.toChar()).forEach { char ->
                val charImage = getCharImage(font, char) ?: return@forEach

                rowHeight = max(rowHeight, charImage.height)

                if (x + charImage.width >= TEXTURE_SIZE) {
                    y += rowHeight
                    x = 0
                    rowHeight = 0
                }

                check(y + charImage.height < TEXTURE_SIZE) { "Can't load font glyphs. Texture size is too small" }

                graphics.drawImage(charImage, x, y, null)

                val size = Vec2d(charImage.width, charImage.height)
                val uv1 = Vec2d(x, y) * ONE_TEXEL_SIZE
                val uv2 = Vec2d(x, y).plus(size) * ONE_TEXEL_SIZE

                charMap[char.code] = CharInfo(size, uv1, uv2)
                fontHeight = max(fontHeight, size.y)

                x += charImage.width
            }

            fontTexture = MipmapTexture(image)
        }

        Lambda.LOG.info("Font ${font.fontName} loaded with ${charMap.size} characters (${time}ms)")
    }

    fun bind() {
        with(fontTexture) {
            bind()
            setLOD(FontSettings.lodBias.toFloat())
        }
    }

    fun getChar(char: Char): CharInfo? =
        charMap[char.code]

    companion object {
        // The size cannot be bigger than 2^15 because the rasterizer needs to be fed with dimensions that when multiplied together are less than 2^31
        // This can be bypassed by using a custom rasterizer, but it's not worth the effort
        // The size is also limited by the java heap size, as the image is stored in memory
        // and then uploaded to the GPU
        // Since most Lambda users probably have bad pc, the default size is 2048, which includes latin, cyrillic, greek and arabic
        // and in the future we could grow the textures when needed
        private val TEXTURE_SIZE = FontSettings.amountOfGlyphs * 2
        private val ONE_TEXEL_SIZE = 1.0 / TEXTURE_SIZE
    }
}
