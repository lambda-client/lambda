package com.lambda.graphics.renderer.gui.font

import com.lambda.core.lifecycle.Loadable
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
            return "Loaded ${entries.size} emoji pools"
        }
    }
}
