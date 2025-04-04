/*
 * Copyright 2025 Lambda
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

package com.lambda.graphics.renderer.gui

import com.lambda.graphics.buffer.vertex.attributes.VertexAttrib
import com.lambda.graphics.pipeline.VertexBuilder
import com.lambda.graphics.renderer.gui.font.core.GlyphInfo
import com.lambda.graphics.renderer.gui.font.core.LambdaAtlas.get
import com.lambda.graphics.renderer.gui.font.core.LambdaAtlas.height
import com.lambda.graphics.shader.Shader.Companion.shader
import com.lambda.graphics.texture.TextureOwner.bind
import com.lambda.module.modules.client.ClickGui
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
object FontRenderer : AbstractGUIRenderer(VertexAttrib.Group.FONT, shader("renderer/font")) {
    private val chars get() = RenderSettings.textFont
    private val emojis get() = RenderSettings.emojiFont

    private val shadowShift get() = RenderSettings.shadowShift * 10.0
    private val baselineOffset get() = RenderSettings.baselineOffset * 2.0f - 16f
    private val gap get() = RenderSettings.gap * 0.5f - 0.8f

    /**
     * Renders a text string at a specified position with configurable color, scale, shadow, and emoji parsing
     *
     * @param text The text to render.
     * @param position The position to render the text.
     * @param color The color of the text.
     * @param scale The scale factor of the text.
     * @param shadow Whether to render a shadow for the text.
     * @param parseEmoji Whether to parse and render emojis in the text.
     */
    fun drawString(
        text: String,
        position: Vec2d = Vec2d.ZERO,
        color: Color = Color.WHITE,
        scale: Double = ClickGui.fontScale,
        shadow: Boolean = true,
        parseEmoji: Boolean = LambdaMoji.isEnabled,
        shade: Boolean = false
    ) = render(shade) {
        shader["u_FontTexture"] = 0
        shader["u_EmojiTexture"] = 1
        shader["u_SDFMin"] = RenderSettings.sdfMin
        shader["u_SDFMax"] = RenderSettings.sdfMax

        bind(chars, emojis)

        upload {
            processText(text, color, scale, shadow, parseEmoji) { char, pos1, pos2, col, _ ->
                buildGlyph(char, position, pos1, pos2, col)
            }
        }
    }

    /**
     * Renders a single glyph at the specified position with the given scale and color
     *
     * @param glyph The glyph information
     * @param position The rendering position where the glyph will be drawn
     * @param color The color of the glyph
     * @param scale The scale factor of the glyph
     */
    fun drawGlyph(
        glyph: GlyphInfo,
        position: Vec2d,
        color: Color = Color.WHITE,
        scale: Double = ClickGui.fontScale,
        shade: Boolean = false
    ) = render(shade) {
        shader["u_FontTexture"] = 0
        shader["u_EmojiTexture"] = 1
        shader["u_SDFMin"] = RenderSettings.sdfMin
        shader["u_SDFMax"] = RenderSettings.sdfMax

        bind(chars, emojis)

        val actualScale = getScaleFactor(scale)
        val scaledSize = glyph.size * actualScale

        val posY = getHeight(scale) * -0.5 + baselineOffset * actualScale
        val pos1 = Vec2d(0.0, posY) * actualScale
        val pos2 = pos1 + scaledSize

        upload {
            buildGlyph(glyph, position, pos1, pos2, color)
        }
    }

    /**
     * Renders a single glyph at a given position.
     *
     * @param glyph The glyph information to render.
     * @param origin The position to start from
     * @param pos1 The starting position of the glyph.
     * @param pos2 The end position of the glyph
     * @param color The color of the glyph.
     */
    private fun VertexBuilder.buildGlyph(
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

        buildQuad(
            vertex {
                vec3m(x1, y1).vec2(glyph.uv1.x, glyph.uv1.y).color(color)
            },
            vertex {
                vec3m(x1, y2).vec2(glyph.uv1.x, glyph.uv2.y).color(color)
            },
            vertex {
                vec3m(x2, y2).vec2(glyph.uv2.x, glyph.uv2.y).color(color)
            },
            vertex {
                vec3m(x2, y1).vec2(glyph.uv2.x, glyph.uv1.y).color(color)
            }
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
        scale: Double = ClickGui.fontScale,
        parseEmoji: Boolean = LambdaMoji.isEnabled,
    ): Double {
        var width = 0.0
        var gaps = -1

        processText(text, scale = scale, parseEmoji = parseEmoji) { char, _, _, _, isShadow ->
            if (isShadow) return@processText
            width += char.width; gaps++
        }

        return (width + gaps.coerceAtLeast(0) * gap) * getScaleFactor(scale)
    }

    /**
     * Computes the effective height of the rendered text
     *
     * The height is derived from the current font's base height, adjusted by a scaling factor
     * that ensures consistent visual proportions
     *
     * @param scale The scale factor for the height calculation.
     * @return The height of the text at the specified scale.
     */
    fun getHeight(scale: Double = 1.0) = chars.height * getScaleFactor(scale) * 0.7

    /**
     * Processes a text string by iterating over its characters and emojis, computing rendering positions, and invoking a block for each glyph
     *
     * @param text The text to iterate over.
     * @param color The color of the text.
     * @param scale The scale of the text.
     * @param shadow Whether to render a shadow.
     * @param parseEmoji Whether to parse and include emojis.
     * @param block The function to apply to each character or emoji glyph.
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

        fun drawGlyph(info: GlyphInfo?, color: Color, isShadow: Boolean = false) {
            if (info == null) return

            val scaledSize = info.size * actualScale
            val pos1 = Vec2d(posX, posY) + shadowShift * actualScale * isShadow.toInt()
            val pos2 = pos1 + scaledSize

            block(info, pos1, pos2, color, isShadow)
            if (!isShadow) posX += scaledSize.x + scaledGap
        }

        val parsed = if (parseEmoji) emojis.parse(text) else mutableListOf()

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

                    if (shadow && RenderSettings.shadow) drawGlyph(glyph, shadowColor, true)
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
     * Calculates the scale factor for the text based on the provided scale.
     *
     * @param scale The base scale factor.
     * @return The adjusted scale factor.
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
