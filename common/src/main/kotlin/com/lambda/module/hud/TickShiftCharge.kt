package com.lambda.module.hud

import com.lambda.graphics.animation.Animation.Companion.exp
import com.lambda.module.HudModule
import com.lambda.module.modules.client.ClickGui
import com.lambda.module.modules.client.GuiSettings
import com.lambda.module.modules.client.GuiSettings.primaryColor
import com.lambda.module.modules.movement.TickShift
import com.lambda.module.tag.ModuleTag
import com.lambda.util.math.multAlpha
import com.lambda.util.math.Rect
import java.awt.Color

object TickShiftCharge : HudModule(
    name = "TickShiftCharge",
    defaultTags = setOf(ModuleTag.CLIENT),
) {
    private val isActive get() = TickShift.isEnabled && TickShift.isActive && TickShift.boost
    private val activeAnimation by animation.exp(0.0, 1.0, 0.6, ::isActive)

    private val progress get() = if (!TickShift.isActive) 0.0
    else (TickShift.balance / TickShift.maxBalance.toDouble()).coerceIn(0.0..1.0)

    private val renderProgress by animation.exp(::progress, 0.8)

    override val width = 70.0
    override val height = 14.0

    init {
        onRender {
            filled.build(
                rect = rect,
                roundRadius = ClickGui.windowRadius,
                color =  GuiSettings.backgroundColor,
                shade = GuiSettings.shadeBackground
            )

            val padding = 1.0
            filled.build(
                rect = Rect.basedOn(rect.leftTop, rect.size.x * renderProgress, rect.size.y).shrink(padding),
                roundRadius = ClickGui.windowRadius - padding,
                color = GuiSettings.mainColor.multAlpha(0.3),
                shade = true
            )

            outline.build(
                rect = rect,
                roundRadius = ClickGui.windowRadius,
                color = (if (GuiSettings.shadeBackground) Color.WHITE else primaryColor).multAlpha(activeAnimation),
                glowRadius = ClickGui.glowRadius * activeAnimation,
                shade = true
            )
        }
    }
}
