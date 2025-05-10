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

package com.lambda.graphics.renderer.gui.font

import com.lambda.graphics.pipeline.PersistentBuffer
import com.lambda.graphics.pipeline.VertexBuilder
import com.lambda.graphics.renderer.gui.FontRenderer
import com.lambda.graphics.renderer.gui.FontRenderer.chars
import com.lambda.graphics.renderer.gui.FontRenderer.emojis
import com.lambda.graphics.renderer.gui.FontRenderer.getHeight
import com.lambda.graphics.renderer.gui.font.core.GlyphInfo
import com.lambda.graphics.renderer.gui.font.core.LambdaAtlas.get
import com.lambda.graphics.renderer.gui.font.core.LambdaAtlas.height
import com.lambda.module.modules.client.LambdaMoji
import com.lambda.module.modules.client.RenderSettings
import com.lambda.module.modules.client.RenderSettings.baselineOffset
import com.lambda.module.modules.client.RenderSettings.gap
import com.lambda.module.modules.client.RenderSettings.shadowShift
import com.lambda.util.math.MathUtils.toInt
import com.lambda.util.math.Vec2d
import org.lwjgl.opengl.GL32C.GL_ARRAY_BUFFER
import org.lwjgl.opengl.GL32C.GL_ELEMENT_ARRAY_BUFFER
import java.awt.Color

// ToDo: use different attribute set based on the string simplicity
class CachedString(
    string: String,
    shadow: Boolean
) {
    val vbo: PersistentBuffer
    val ibo: PersistentBuffer

    val cachedWidth = getStringWidth(string)

    init {
        /* Build vertices and indices */
        val builder = VertexBuilder().use {
            processText(string, shadow) { glyph, pos1, pos2, c, _ ->
                val x1 = pos1.x
                val y1 = pos1.y
                val x2 = pos2.x
                val y2 = pos2.y

                buildQuad(
                    vertex {
                        vec2(x1, y1).vec2(glyph.uv1.x, glyph.uv1.y).color(c)
                    },
                    vertex {
                        vec2(x1, y2).vec2(glyph.uv1.x, glyph.uv2.y).color(c)
                    },
                    vertex {
                        vec2(x2, y2).vec2(glyph.uv2.x, glyph.uv2.y).color(c)
                    },
                    vertex {
                        vec2(x2, y1).vec2(glyph.uv2.x, glyph.uv1.y).color(c)
                    }
                )
            }
        }

        /* Create gl buffers and upload */
        val vao = FontRenderer.vao
        vbo = PersistentBuffer(GL_ARRAY_BUFFER, vao.attributes.stride, builder.vertices.size)
        ibo = PersistentBuffer(GL_ELEMENT_ARRAY_BUFFER, Int.SIZE_BYTES, builder.indices.size)
        vao.linkVbo(vbo)

        builder.uploadVertices(vbo.byteBuffer)
        vbo.upload()

        builder.uploadIndices(ibo.byteBuffer)
        ibo.upload()
    }

    companion object {
        val SCALE_FACTOR get() = 8.5 / chars.height

        /**
         * Processes a text string by iterating over its characters and emojis, computing rendering positions, and invoking a block for each glyph
         *
         * @param text The text to iterate over.
         * @param shadow Whether to render a shadow.
         * @param block The function to apply to each character or emoji glyph.
         */
        fun processText(
            text: String,
            shadow: Boolean,
            block: (GlyphInfo, Vec2d, Vec2d, Color, Boolean) -> Unit
        ) {
            val scaledGap = gap * SCALE_FACTOR

            var posX = 0.0
            var posY = baselineOffset * SCALE_FACTOR - getHeight() * 0.5

            fun drawGlyph(info: GlyphInfo?, color: Color, isShadow: Boolean = false) {
                if (info == null) return

                val scaledSize = info.size * SCALE_FACTOR
                val pos1 = Vec2d(posX, posY) + shadowShift * SCALE_FACTOR * isShadow.toInt()
                val pos2 = pos1 + scaledSize

                block(info, pos1, pos2, color, isShadow)
                if (!isShadow) posX += scaledSize.x + scaledGap
            }

            val parsed = if (LambdaMoji.isEnabled) emojis.parse(text) else mutableListOf()

            fun processTextSection(section: String, hasEmojis: Boolean) {
                if (section.isEmpty()) return
                if (!LambdaMoji.isEnabled || parsed.isEmpty() || !hasEmojis) {
                    // Draw simple characters if no emojis are present
                    section.forEach { char ->
                        // Logic for control characters
                        when (char) {
                            '\n', '\r' -> { posX = 0.0; posY += chars.height * SCALE_FACTOR; return@forEach }
                        }

                        val glyph = chars[char] ?: return@forEach

                        // ToDo: Implement colorcodes or remove color from attributes
                        val color = Color.WHITE
                        val shadowColor = Color(
                            (color.red * RenderSettings.shadowBrightness).toInt(),
                            (color.green * RenderSettings.shadowBrightness).toInt(),
                            (color.blue * RenderSettings.shadowBrightness).toInt(),
                            color.alpha
                        )

                        if (shadow) drawGlyph(glyph, shadowColor, true)
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
                    drawGlyph(emojis[emoji], Color.WHITE)

                    // Process the rest of the text after the emoji
                    processTextSection(postEmojiText, hasEmojis = true)
                }
            }

            // Start processing the full text
            processTextSection(text, hasEmojis = parsed.isNotEmpty())
        }

        fun getStringWidth(string: String): Double {
            var width = 0.0
            var gaps = -1

            processText(string, false) { char, _, _, _, _ ->
                width += char.width; gaps++
            }

            return (width + gaps.coerceAtLeast(0) * gap) * SCALE_FACTOR
        }
    }
}
