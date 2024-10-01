package com.lambda.gui.api

import com.lambda.graphics.renderer.gui.font.FontRenderer
import com.lambda.graphics.renderer.gui.font.LambdaFont
import com.lambda.graphics.renderer.gui.font.LambdaEmoji
import com.lambda.graphics.renderer.gui.rect.FilledRectRenderer
import com.lambda.graphics.renderer.gui.rect.OutlineRectRenderer
import com.lambda.threading.mainThread

class RenderLayer {
    val filled by mainThread(::FilledRectRenderer)
    val outline by mainThread(::OutlineRectRenderer)

    val font by mainThread {
        FontRenderer(
            LambdaFont.FiraSansRegular,
            LambdaEmoji.Twemoji,
        )
    }

    private val boldFont0 = lazy {
        FontRenderer(
            LambdaFont.FiraSansBold,
            LambdaEmoji.Twemoji,
        )
    }

    val boldFont by boldFont0

    fun render() {
        filled.render()
        outline.render()
        font.render()

        if (boldFont0.isInitialized()) boldFont0.value.render()
    }
}
