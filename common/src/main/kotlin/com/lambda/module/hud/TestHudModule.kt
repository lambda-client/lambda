package com.lambda.module.hud

import com.lambda.module.HudModule
import java.awt.Color

object TestHudModule : HudModule(
    "TestHudModule",
    enabledByDefault = true
) {
    override val width = 100.0
    override val height = 30.0

    init {
        onRender {
            filled.build(rect, 2.0, Color(180, 180, 180, 180), true)
            outline.build(rect, 2.0, shade = true)
        }
    }
}