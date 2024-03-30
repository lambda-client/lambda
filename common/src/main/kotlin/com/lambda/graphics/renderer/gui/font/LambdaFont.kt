package com.lambda.graphics.renderer.gui.font

import com.lambda.graphics.renderer.gui.font.glyph.FontGlyphs
import com.lambda.util.LambdaResource
import java.awt.Font

enum class LambdaFont(fontName: String) {
    FiraSansRegular("FiraSans-Regular"),
    FiraSansBold("FiraSans-Bold");

    val glyphs = FontGlyphs(getFont(fontName))

    operator fun get(char: Char) = glyphs.getChar(char)

    private fun getFont(name: String): Font {
        val resource = LambdaResource("fonts/$name.ttf")
        val stream = resource.stream ?: throw IllegalStateException("Failed to locate font $name")
        return Font.createFont(Font.TRUETYPE_FONT, stream).deriveFont(64.0f)
    }
}