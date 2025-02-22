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

package com.lambda.graphics.renderer.gui.font

import com.lambda.graphics.buffer.VertexPipeline
import com.lambda.graphics.buffer.vertex.attributes.VertexAttrib
import com.lambda.graphics.renderer.gui.AbstractGUIRenderer
import com.lambda.graphics.renderer.gui.font.core.GlyphInfo
import com.lambda.graphics.renderer.gui.font.core.LambdaAtlas.get
import com.lambda.graphics.renderer.gui.font.core.LambdaAtlas.height
import com.lambda.graphics.shader.Shader.Companion.shader
import com.lambda.graphics.texture.TextureOwner.bind
import com.lambda.module.modules.client.LambdaMoji
import com.lambda.module.modules.client.RenderSettings
import com.lambda.util.math.MathUtils.toInt
import com.lambda.util.math.Vec2d
import com.lambda.util.math.a
import com.lambda.util.math.setAlpha
import java.awt.Color

/**
 * Renders text and emoji glyphs using a shader-based font rendering system.
 * This class handles text and emoji rendering, shadow effects, and text scaling.
 */
object FontRenderer : AbstractGUIRenderer(VertexAttrib.Group.FONT, shader("font/font")) {
    private val chars get() = RenderSettings.textFont
    private val emojis get() = RenderSettings.emojiFont

    private val shadowShift get() = RenderSettings.shadowShift * 5.0
    private val baselineOffset get() = RenderSettings.baselineOffset * 2.0f - 10f
    private val gap get() = RenderSettings.gap * 0.5f - 0.8f

    /**
     * Renders a text string at a specified position with configurable color, scale, shadow, and emoji parsing.
     *
     * This function sets up shader parameters for font and emoji textures, binds the current font assets,
     * and processes the text to generate glyph vertices for rendering.
     *
     * @param text the text string to render.
     * @param position the position at which the text is drawn.
     * @param color the color to use for the text.
     * @param scale the scale factor for the text size.
     * @param shadow if true, renders a shadow effect along with the text.
     * @param parseEmoji if true, parses and renders emoji characters in the text.
     */
    fun drawString(
        text: String,
        position: Vec2d = Vec2d.ZERO,
        color: Color = Color.WHITE,
        scale: Double = 1.0,
        shadow: Boolean = RenderSettings.shadow,
        parseEmoji: Boolean = LambdaMoji.isEnabled
    ) = render {
        shader["u_FontTexture"] = 0
        shader["u_EmojiTexture"] = 1
        shader["u_SDFMin"] = 0.3
        shader["u_SDFMax"] = 1.0

        bind(chars, emojis)

        processText(text, color, scale, shadow, parseEmoji) { char, pos1, pos2, col, _ ->
            buildGlyph(char, position, pos1, pos2, col)
        }
    }

    /**
     * Renders a single glyph at the specified position with the given scale and color.
     *
     * This function sets up shader parameters and binds the current font and emoji textures before
     * computing the effective scale and adjusted positions based on the glyph’s dimensions and the current
     * baseline offset. It then builds and renders the glyph’s quad.
     *
     * @param glyph the glyph information containing size and texture coordinates.
     * @param position the rendering position where the glyph will be drawn.
     * @param color the color applied to the glyph (default is [Color.WHITE]).
     * @param scale the scale factor for the glyph (default is 1.0).
     */
    fun drawGlyph(
        glyph: GlyphInfo,
        position: Vec2d,
        color: Color = Color.WHITE,
        scale: Double = 1.0
    ) = render {
        shader["u_FontTexture"] = 0
        shader["u_EmojiTexture"] = 1
        shader["u_SDFMin"] = 0.3
        shader["u_SDFMax"] = 1.0

        bind(chars, emojis)

        val actualScale = getScaleFactor(scale)
        val scaledSize = glyph.size * actualScale

        val posY = getHeight(scale) * -0.5 + baselineOffset * actualScale
        val pos1 = Vec2d(0.0, posY) * actualScale
        val pos2 = pos1 + scaledSize

        buildGlyph(glyph, position, pos1, pos2, color)
    }

    /**
     * Constructs and adds a quad for the specified glyph to the vertex pipeline.
     *
     * The quad's vertices are computed by offsetting the provided boundary positions (`pos1` and `pos2`)
     * with the given `origin`. Each vertex is assigned texture coordinates derived from the glyph data and
     * tinted with the specified color.
     *
     * @param glyph The glyph providing texture mapping coordinates.
     * @param origin The positional offset applied to the glyph's vertices.
     * @param pos1 One corner of the glyph's bounding rectangle.
     * @param pos2 The diagonally opposite corner of the glyph's bounding rectangle.
     * @param color The color to apply to the glyph.
     */
    private fun VertexPipeline.buildGlyph(
        glyph: GlyphInfo,
        origin: Vec2d = Vec2d.ZERO,
        pos1: Vec2d,
        pos2: Vec2d,
        color: Color,
    ) {
        val x1 = pos1.x + origin.x
        val y1 = pos1.y + origin.y
        val x2 = pos2.x + origin.x
        val y2 = pos2.y + origin.y

        grow(4)

        putQuad(
            vec3m(x1, y1, 0.0).vec2(glyph.uv1.x, glyph.uv1.y).color(color).end(),
            vec3m(x1, y2, 0.0).vec2(glyph.uv1.x, glyph.uv2.y).color(color).end(),
            vec3m(x2, y2, 0.0).vec2(glyph.uv2.x, glyph.uv2.y).color(color).end(),
            vec3m(x2, y1, 0.0).vec2(glyph.uv2.x, glyph.uv1.y).color(color).end()
        )
    }

    /**
     * Calculates the width of the specified text.
     *
     * @param text The text to measure.
     * @param scale The scale factor for the width calculation.
     * @param parseEmoji Whether to include emojis in the width calculation.
     * @return The width of the text at the specified scale.
     */
    fun getWidth(
        text: String,
        scale: Double = 1.0,
        parseEmoji: Boolean = LambdaMoji.isEnabled,
    ): Double {
        var width = 0.0
        processText(text, scale = scale, parseEmoji = parseEmoji) {
                char, _, _, _, isShadow -> width += char.width * isShadow.toInt()
        }
        return width * getScaleFactor(scale)
    }

    /**
 * Computes the effective height of the rendered text.
 *
 * The height is derived from the current font's base height, adjusted by a scaling factor
 * that ensures consistent visual proportions.
 *
 * @param scale the scaling factor to apply (default is 1.0)
 * @return the effective height of the text for the provided scale
 */
    fun getHeight(scale: Double = 1.0) = chars.height * getScaleFactor(scale) * 0.7

    /**
     * Processes a text string by iterating over its characters and emojis, computing rendering positions, and invoking a block for each glyph.
     *
     * The function calculates an adjusted scale factor and applies a gap between glyphs as well as a baseline offset for proper
     * vertical alignment. For every glyph, if shadow rendering is enabled, it first invokes the block for a shadow glyph (using an offset)
     * followed by the main glyph. It handles control characters (such as newlines) to adjust positioning, and when emoji parsing is enabled,
     * it recursively splits the text to separately process emoji sequences and regular characters.
     *
     * @param text the string to process.
     * @param color the base color used for rendering the glyphs.
     * @param scale the scale factor applied to the text size.
     * @param shadow if true, renders a shadow glyph before the main glyph.
     * @param parseEmoji if true, detects and processes emoji sequences in the text.
     * @param block a function that is invoked for each glyph. It receives:
     *              - GlyphInfo: the glyph information.
     *              - Vec2d: the starting position of the glyph.
     *              - Vec2d: the ending position of the glyph (computed from its size).
     *              - Color: the color to render the glyph.
     *              - Boolean: a flag indicating whether the glyph represents a shadow.
     */
    private fun processText(
        text: String,
        color: Color = Color.WHITE,
        scale: Double = 1.0,
        shadow: Boolean = RenderSettings.shadow,
        parseEmoji: Boolean = LambdaMoji.isEnabled,
        block: (GlyphInfo, Vec2d, Vec2d, Color, Boolean) -> Unit
    ) {
        val actualScale = getScaleFactor(scale)
        val scaledGap = gap * actualScale

        val shadowColor = getShadowColor(color)
        val emojiColor = color.setAlpha(color.a)

        var posX = 0.0
        var posY = getHeight(scale) * -0.5 + baselineOffset * actualScale

        /**
         * Renders a glyph with the provided information and styling.
         *
         * If the glyph information is null, no rendering occurs. The function calculates the glyph's scaled size
         * and computes its drawing coordinates based on the current position and scale factor. A non-zero offset
         * indicates that the glyph is rendered as a shadow; in this case, the horizontal drawing position remains
         * unchanged. Otherwise, the drawing position is advanced based on the glyph's width and a predefined gap.
         *
         * @param info The glyph information containing its size and other rendering details; if null, the glyph is not drawn.
         * @param color The color applied to the glyph.
         * @param offset An optional offset for positioning; a non-zero value flags the glyph as a shadow.
         */
        fun drawGlyph(info: GlyphInfo?, color: Color, offset: Double = 0.0) {
            if (info == null) return
            val isShadow = offset != 0.0

            val scaledSize = info.size * actualScale
            val pos1 = Vec2d(posX, posY) + offset * actualScale
            val pos2 = pos1 + scaledSize

            block(info, pos1, pos2, color, isShadow)
            if (!isShadow) posX += scaledSize.x + scaledGap
        }

        val parsed = if (parseEmoji) emojis.parse(text) else mutableListOf()

        /**
         * Processes a segment of text for rendering, handling regular characters and emojis.
         *
         * The function iterates over the given text section, drawing each glyph while managing control characters
         * such as newlines and carriage returns to update the rendering position. When emojis are enabled and
         * detected, it splits the text to render emoji characters separately, ensuring proper text layout.
         *
         * @param section The portion of text to be processed.
         * @param hasEmojis Indicates whether the section may contain emojis that require special handling.
         */
        fun processTextSection(section: String, hasEmojis: Boolean) {
            if (section.isEmpty()) return
            if (!parseEmoji || parsed.isEmpty() || !hasEmojis) {
                // Draw simple characters if no emojis are present
                section.forEach { char ->
                    // Logic for control characters
                    when (char) {
                        '\n', '\r' -> { posX = 0.0; posY += chars.height * actualScale; return@forEach }
                    }

                    val glyph = chars[char] ?: return@forEach

                    if (shadow && shadowShift > 0.0) drawGlyph(glyph, shadowColor, shadowShift)
                    drawGlyph(glyph, color)
                }
            } else {
                // Only compute the first parsed emoji to avoid duplication
                // This is important in order to keep the parsed ranges valid
                // If you do not this, you will get out of bounds positions
                // due to slicing
                val emoji = parsed.removeFirstOrNull() ?: return

                // Iterate the emojis from left to right
                val start = section.indexOf(emoji)
                val end = start + emoji.length

                val preEmojiText = section.substring(0, start)
                val postEmojiText = section.substring(end)

                // Draw the text without emoji
                processTextSection(preEmojiText, hasEmojis = false)

                // Draw the emoji
                drawGlyph(emojis[emoji], emojiColor)

                // Process the rest of the text after the emoji
                processTextSection(postEmojiText, hasEmojis = true)
            }
        }

        // Start processing the full text
        processTextSection(text, hasEmojis = parsed.isNotEmpty())
    }

    /**
 * Computes an adjusted scale factor for text rendering.
 *
 * This method applies a constant multiplier (8.5) to the base scale and normalizes it
 * by the current text font's height, ensuring consistent text sizing across different fonts.
 *
 * @param scale the input base scale factor.
 * @return the resulting adjusted scale factor.
 */
    fun getScaleFactor(scale: Double): Double = scale * 8.5 / chars.height

    /**
     * Calculates the shadow color by adjusting the brightness of the input color.
     *
     * @param color The original color.
     * @return The modified shadow color.
     */
    fun getShadowColor(color: Color): Color = Color(
        (color.red * RenderSettings.shadowBrightness).toInt(),
        (color.green * RenderSettings.shadowBrightness).toInt(),
        (color.blue * RenderSettings.shadowBrightness).toInt(),
        color.alpha
    )
}
