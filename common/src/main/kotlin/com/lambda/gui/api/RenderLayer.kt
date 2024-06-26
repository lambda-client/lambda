package com.lambda.gui.api

import com.lambda.graphics.renderer.gui.font.FontRenderer
import com.lambda.graphics.renderer.gui.font.LambdaFont
import com.lambda.graphics.renderer.gui.font.LambdaEmoji
import com.lambda.graphics.renderer.gui.rect.FilledRectRenderer
import com.lambda.graphics.renderer.gui.rect.OutlineRectRenderer

class RenderLayer {
    val filled = FilledRectRenderer()
    val outline = OutlineRectRenderer()
    val font = FontRenderer(
        LambdaFont.FiraSansRegular,
        LambdaEmoji.Twemoji,
    )

    fun render() {
        filled.render()
        outline.render()
        font.render()
    }
}
