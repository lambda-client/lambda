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

    object Loader : Loadable {
        override fun load(): String {
            entries.forEach(LambdaFont::loadGlyphs)
            return "Loaded ${entries.size} fonts"
        }
    }
}
