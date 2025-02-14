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

import com.lambda.graphics.renderer.gui.font.core.LambdaAtlas.buildBuffer

enum class LambdaEmoji(val url: String) {
    Twemoji("https://github.com/Edouard127/emoji-generator/releases/latest/download/emojis.zip");

    private val emojiRegex = Regex(":[a-zA-Z0-9_]+:")

    /**
     * Parses the emojis in the given text.
     *
     * @param text The text to parse.
     *
     * @return A list of parsed strings that does not contain the colons
     */
    fun parse(text: String): MutableList<String> =
        emojiRegex.findAll(text).map { it.value }.toMutableList()

    fun load(): String {
        entries.forEach { it.buildBuffer() }
        return "Loaded ${entries.size} emoji sets"
    }
}
