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
import com.lambda.graphics.renderer.gui.font.glyph.FontGlyphs
import com.lambda.util.LambdaResource
import java.awt.Font

enum class LambdaFont(private val fontName: String) {
    FiraSansRegular("FiraSans-Regular"),
    FiraSansBold("FiraSans-Bold");

    lateinit var glyphs: FontGlyphs

    operator fun get(char: Char) = glyphs.getChar(char)

    fun loadGlyphs() {
        val resource = LambdaResource("fonts/$fontName.ttf")
        val stream = resource.stream ?: throw IllegalStateException("Failed to locate font $fontName")
        val font = Font.createFont(Font.TRUETYPE_FONT, stream).deriveFont(64.0f)
        glyphs = FontGlyphs(font)
    }

    object FontLoader : Loadable {
        override fun load(): String {
            entries.forEach(LambdaFont::loadGlyphs)
            return "Loaded ${entries.size} fonts"
        }
    }
}
