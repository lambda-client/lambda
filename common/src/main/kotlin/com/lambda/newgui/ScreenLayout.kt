package com.lambda.newgui

import com.lambda.graphics.RenderMain
import com.lambda.newgui.component.core.UIBuilder
import com.lambda.newgui.component.layout.Layout

class ScreenLayout : Layout(owner = null, useBatching = false, batchChildren = true) {
    init {
        onRender {
            size = RenderMain.screenSize
        }
    }

    companion object {
        /**
         * Creates gui layout
         */
        @UIBuilder
        fun gui(name: String, block: ScreenLayout.() -> Unit) =
            LambdaScreen(name, ScreenLayout().apply(block))
    }
}