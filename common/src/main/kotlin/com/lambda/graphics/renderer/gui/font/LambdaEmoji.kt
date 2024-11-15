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

import com.lambda.core.Loadable
import com.lambda.graphics.renderer.gui.font.glyph.EmojiGlyphs

enum class LambdaEmoji(private val zipUrl: String) {
    Twemoji("https://github.com/Edouard127/emoji-generator/releases/latest/download/emojis.zip");

    lateinit var glyphs: EmojiGlyphs

    operator fun get(emoji: String) = glyphs.emojiFromString(emoji)

    fun loadGlyphs() {
        glyphs = EmojiGlyphs(zipUrl)
    }

    object Loader : Loadable {
        override fun load(): String {
            entries.forEach(LambdaEmoji::loadGlyphs)
            return "Loaded ${entries.size} emoji sets with a total of ${entries.sumOf { it.glyphs.count }} emojis"
        }
    }
}
