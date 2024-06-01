package com.lambda.graphics.renderer.gui.font

import com.lambda.core.Loadable
import com.lambda.graphics.renderer.gui.font.glyph.EmojiGlyphs

enum class LambdaMoji(private val zipUrl: String) {
    Twemoji("https://github.com/Edouard127/emoji-generator/releases/latest/download/emojis.zip");

    lateinit var glyphs: EmojiGlyphs

    operator fun get(emoji: String) = glyphs.getEmoji(emoji)

    fun loadGlyphs() {
        glyphs = EmojiGlyphs(zipUrl)
    }

    object Loader : Loadable {
        override fun load(): String {
            LambdaMoji.entries.forEach(LambdaMoji::loadGlyphs)
            return "Loaded ${LambdaMoji.entries.size} emoji sets"
        }
    }
}
