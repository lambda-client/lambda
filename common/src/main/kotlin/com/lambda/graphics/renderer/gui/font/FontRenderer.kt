package com.lambda.graphics.renderer.gui.font

import com.lambda.graphics.buffer.vao.VAO
import com.lambda.graphics.buffer.vao.vertex.VertexAttrib
import com.lambda.graphics.buffer.vao.vertex.VertexMode
import com.lambda.graphics.renderer.gui.font.glyph.GlyphInfo
import com.lambda.graphics.shader.Shader
import com.lambda.module.modules.client.FontSettings
import com.lambda.util.math.ColorUtils.a
import com.lambda.util.math.ColorUtils.setAlpha
import com.lambda.util.math.Vec2d
import java.awt.Color

class FontRenderer(
    private val font: LambdaFont,
    private val emojis: LambdaMoji
) {
    private val vao = VAO(VertexMode.TRIANGLES, VertexAttrib.Group.FONT)

    private val scaleMultiplier = 1.0
    private val emojiRegex = Regex(":[a-zA-Z0-9_]+:")

    /**
     * Parses the emojis in the given text.
     *
     * @param text The text to parse.
     * @return A list of pairs containing the glyph info and the range of the emoji in the text.    
     */
    fun parseEmojis(text: String) =
        mutableListOf<Pair<GlyphInfo, IntRange>>().apply {
            emojiRegex.findAll(text).forEach { match ->
                val emojiKey = match.value.substring(1, match.value.length - 1)
                val charInfo = emojis[emojiKey] ?: return@forEach
                add(charInfo to match.range)
            }
        }

    /**
     * Builds the vertex array for rendering the text.
     */
    fun build(
        text: String,
        position: Vec2d,
        color: Color = Color.WHITE,
        scale: Double = 1.0,
        shadow: Boolean = true
    ) = vao.use {
        iterateText(text, scale, shadow, color) { char, pos1, pos2, color ->
            grow(4)
            putQuad(
                vec2(pos1.x + position.x, pos1.y + position.y).vec2(char.uv1.x, char.uv1.y).color(color).end(),
                vec2(pos1.x + position.x, pos2.y + position.y).vec2(char.uv1.x, char.uv2.y).color(color).end(),
                vec2(pos2.x + position.x, pos2.y + position.y).vec2(char.uv2.x, char.uv2.y).color(color).end(),
                vec2(pos2.x + position.x, pos1.y + position.y).vec2(char.uv2.x, char.uv1.y).color(color).end()
            )
        }
    }

    /**
     * Calculates the width of the given text.
     */
    fun getWidth(text: String, scale: Double = 1.0): Double {
        var width = 0.0
        iterateText(text, scale, false) { char, _, _, _ -> width += char.width + gap }
        return width * getScaleFactor(scale)
    }

    /**
     * Calculates the height of the text.
     *
     * The values are hardcoded
     * We do not need to ask the emoji font since the height is smaller
     */
    private fun getHeight(scale: Double = 1.0) = font.glyphs.fontHeight * getScaleFactor(scale) * 0.7

    /**
     * Iterates over each character and emoji in the text.
     *
     * @param text The text to iterate over.
     * @param scale The scale of the text.
     * @param shadow Whether to render a shadow.
     * @param color The color of the text.
     * @param block The block to execute for each character.
     *
     * @see GlyphInfo
     */
    private fun iterateText(
        text: String,
        scale: Double,
        shadow: Boolean,
        color: Color = Color.WHITE,
        block: (GlyphInfo, Vec2d, Vec2d, Color) -> Unit
    ) {
        val actualScale = getScaleFactor(scale)
        val scaledGap = gap * actualScale

        val shadowColor = getShadowColor(color)
        val emojiColor = Color.WHITE.setAlpha(color.a)

        var posX = 0.0
        val posY = getHeight(scale) * -0.5 + baselineOffset * actualScale

        val emojis = parseEmojis(text)

        repeat(text.length) { index ->
            fun draw(info: GlyphInfo, color: Color, offset: Double = 0.0) {
                val scaledSize = info.size * actualScale
                val pos1 = Vec2d(posX, posY) + offset * actualScale
                val pos2 = pos1 + scaledSize

                block(info, pos1, pos2, color)
                if (offset == 0.0) posX += scaledSize.x + scaledGap
            }

            // Check if there's an emoji
            emojis.firstOrNull { index in it.second }?.let { emoji ->
                // Replace first emoji char by an emoji glyph and skip the other ones
                if (index == emoji.second.first) {
                    draw(emoji.first, emojiColor)
                }

                return@repeat
            }

            // Render chars
            font[text[index]]?.let { info ->
                // Draw a shadow before
                if (shadow && FontSettings.shadow && shadowShift > 0.0) {
                    draw(info, shadowColor, shadowShift)
                }

                // Draw actual char over the shadow
                draw(info, color)
            }
        }
    }

    private fun getScaleFactor(scale: Double) = scaleMultiplier * scale * 0.12

    private fun getShadowColor(color: Color): Color {
        return Color(
            (color.red * FontSettings.shadowBrightness).toInt(),
            (color.green * FontSettings.shadowBrightness).toInt(),
            (color.blue * FontSettings.shadowBrightness).toInt(),
            color.alpha
        )
    }

    fun render() {
        shader.use()
        shader["u_EmojiTexture"] = 1

        font.glyphs.bind()
        emojis.glyphs.bind()

        vao.upload()
        vao.render()
        vao.clear()
    }

    companion object {
        private val shader = Shader("renderer/font")

        private val shadowShift get() = FontSettings.shadowShift * 5.0
        private val baselineOffset get() = FontSettings.baselineOffset * 2.0f - 10f
        private val gap get() = FontSettings.gapSetting * 0.5f - 0.8f
    }
}
