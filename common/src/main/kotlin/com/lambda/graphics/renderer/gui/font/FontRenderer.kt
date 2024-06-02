package com.lambda.graphics.renderer.gui.font

import com.lambda.graphics.buffer.vao.IRenderContext
import com.lambda.graphics.buffer.vao.vertex.VertexAttrib
import com.lambda.graphics.buffer.vao.vertex.VertexMode
import com.lambda.graphics.renderer.Renderer
import com.lambda.graphics.renderer.gui.font.glyph.CharInfo
import com.lambda.graphics.shader.Shader
import com.lambda.module.modules.client.FontSettings
import com.lambda.util.math.Vec2d
import java.awt.Color

class FontRenderer(
    private val font: LambdaFont,
    private val emojis: LambdaMoji
) : Renderer(VertexMode.TRIANGLES, VertexAttrib.Group.FONT) {
    private val scaleMultiplier = 1.0
    private val emojiRegex = Regex(":[a-zA-Z0-9_]+:")

    /**
     * Parses the emojis in the given text.
     *
     * @param text The text to parse.
     * @return A list of triples containing the emoji text, start index, and end index.
     */
    fun parseEmojis(text: String): List<Triple<CharInfo, Int, Int>> {
        val result = mutableListOf<Triple<CharInfo, Int, Int>>()
        val matches = emojiRegex.findAll(text)

        for (match in matches) {
            val emojiKey = match.value.substring(1, match.value.length - 1)
            val charInfo = emojis[emojiKey] ?: continue
            result.add(Triple(charInfo, match.range.first, match.range.last))
        }

        return result
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
            putChar(position, pos1, pos2, color, char)
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
     * @see CharInfo
     */
    private fun iterateText(
        text: String,
        scale: Double,
        shadow: Boolean,
        color: Color = Color.WHITE,
        block: (CharInfo, Vec2d, Vec2d, Color) -> Unit
    ) {
        val actualScale = getScaleFactor(scale)
        val scaledShadowShift = shadowShift * actualScale
        val scaledGap = gap * actualScale

        var posX = 0.0
        val posY = getHeight(scale) * -0.5 + baselineOffset * actualScale

        val emojis = parseEmojis(text)

        var index = 0
        while (index < text.length) {
            run { // Because continue is not allowed in lambda
                emojis
                    .firstOrNull { index in it.second..it.third }
                    ?.let { emoji ->
                        val scaledSize = emoji.first.size * actualScale
                        val pos1 = Vec2d(posX, posY)
                        val pos2 = pos1 + scaledSize

                        block(emoji.first, pos1, pos2, color)

                        posX += scaledSize.x + scaledGap
                        index += emoji.third - emoji.second + 1
                        return@run
                    }

                val char = text[index]
                val glyph = font[char] ?: return@run

                val scaledSize = glyph.size * actualScale
                val pos1 = Vec2d(posX, posY)
                val pos2 = pos1 + scaledSize

                if (shadow && FontSettings.shadow) {
                    val shadowPos1 = pos1 + scaledShadowShift
                    val shadowPos2 = shadowPos1 + scaledSize
                    block(glyph, shadowPos1, shadowPos2, getShadowColor(color))
                }

                block(glyph, pos1, pos2, color)

                posX += scaledSize.x + scaledGap
            }

            index++
        }
    }

    private fun IRenderContext.putChar(pos: Vec2d, lt: Vec2d, rb: Vec2d, color: Color, ci: CharInfo) {
        val x = pos.x
        val y = pos.y

        grow(4)

        putQuad(
            vec2(lt.x + x, lt.y + y).vec2(ci.uv1.x, ci.uv1.y).color(color).end(),
            vec2(lt.x + x, rb.y + y).vec2(ci.uv1.x, ci.uv2.y).color(color).end(),
            vec2(rb.x + x, rb.y + y).vec2(ci.uv2.x, ci.uv2.y).color(color).end(),
            vec2(rb.x + x, lt.y + y).vec2(ci.uv2.x, ci.uv1.y).color(color).end()
        )
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

    override fun render() {
        shader.use()

        font.glyphs.bind()

        emojis.glyphs.bind()
        shader["u_EmojiTexture"] = 1

        super.render()
    }

    companion object {
        private val shader = Shader("renderer/font")
        private val shadowShift get() = FontSettings.shadowShift * 4.0
        private val baselineOffset get() = FontSettings.baselineOffset * 2.0f - 10f
        private val gap get() = FontSettings.gapSetting * 0.5f - 0.8f
    }
}
