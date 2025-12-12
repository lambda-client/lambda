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

package com.lambda.module.modules.client

import com.lambda.Lambda.mc
import com.lambda.graphics.renderer.gui.font.core.GlyphInfo
import com.lambda.graphics.renderer.gui.font.core.LambdaAtlas.get
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.math.Vec2d
import net.minecraft.text.OrderedText
import net.minecraft.text.Style
import java.awt.Color

// This is the worst code I have ever wrote in my life
object LambdaMoji : Module(
    name = "LambdaMoji",
    description = "",
    tag = ModuleTag.CLIENT,
    enabledByDefault = false,
) {
    val suggestions by setting("Chat Suggestions", true)

    private val renderQueue = mutableListOf<Triple<GlyphInfo, Vec2d, Color>>()

    init {
        /*listen<RenderEvent.GUI.Scaled> {
            renderQueue.forEach { (glyph, position, color) ->
                drawGlyph(glyph, position, color)
            }

            renderQueue.clear()
        }*/
    }

    // FixMe: Doesn't render properly when the chat scale is modified
    fun parse(text: OrderedText, x: Float, y: Float, color: Int): OrderedText {
        val saved = mutableMapOf<Int, Style>()
        val builder = StringBuilder()

        var absoluteIndex = 0
        text.accept { _, style, codePoint ->
            saved[absoluteIndex++] = style
            builder.appendCodePoint(codePoint)
            true
        }

        var raw = builder.toString()
        StyleEditor.emojiFont.parse(raw)
            .forEach { emoji ->
                val index = raw.indexOf(emoji)
                if (index == -1) return@forEach

                val width = mc.textRenderer.getWidth(raw.substring(0, index))

                // Dude I'm sick of working with the shitcode that is minecraft's codebase :sob:
                val trueColor = when (color) {
                    0x00E0E0E0, 0 -> Color(255, 255, 255, 255)
                    else -> Color(255, 255, 255, (color shr 24 and 0xFF))
                }

                val glyph = StyleEditor.emojiFont[emoji] ?: return@forEach
                renderQueue.add(Triple(glyph, Vec2d(x + width, y), trueColor))

                // Replace the emoji with whitespaces depending on the player's settings
                raw = raw.replaceFirst(emoji, " ")
            }

        val constructed = mutableListOf<OrderedText>()

        // Will not work properly if the emoji is part of the style
        saved.forEach { (index, style) ->
            if (index >= raw.length) return@forEach
            constructed.add(OrderedText.styledForwardsVisitedString(raw.substring(index, index + 1), style))
        }

        return OrderedText.concat(constructed)
    }
}
