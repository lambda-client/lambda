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

package com.lambda.module.hud

import com.lambda.graphics.animation.Animation.Companion.exp
import com.lambda.graphics.renderer.gui.rect.FilledRectRenderer
import com.lambda.graphics.renderer.gui.rect.FilledRectRenderer.filledRect
import com.lambda.graphics.renderer.gui.rect.OutlineRectRenderer
import com.lambda.graphics.renderer.gui.rect.OutlineRectRenderer.outlineRect
import com.lambda.module.HudModule
import com.lambda.module.modules.client.ClickGui
import com.lambda.module.modules.client.GuiSettings
import com.lambda.module.modules.client.GuiSettings.primaryColor
import com.lambda.module.modules.movement.TickShift
import com.lambda.module.tag.ModuleTag
import com.lambda.util.math.Rect
import com.lambda.util.math.multAlpha
import java.awt.Color

object TickShiftCharge : HudModule(
    name = "TickShiftCharge",
    defaultTags = setOf(ModuleTag.CLIENT),
) {
    private val isActive get() = TickShift.isEnabled && TickShift.isActive && TickShift.boost
    private val activeAnimation by animation.exp(0.0, 1.0, 0.6, ::isActive)

    private val renderProgress by animation.exp(0.8) {
        if (!TickShift.isActive) return@exp 0.0

        (TickShift.balance / TickShift.maxBalance.toDouble()).coerceIn(0.0..1.0)
    }

    init {
        build {

        }

        /*onRender {
            filledRect(
                rect = rect,
                roundRadius = ClickGui.roundRadius,
                color = GuiSettings.backgroundColor,
                shade = GuiSettings.shadeBackground
            )

            val padding = 1.0
            filledRect(
                rect = Rect.basedOn(rect.leftTop, rect.size.x * renderProgress, rect.size.y).shrink(padding),
                roundRadius = ClickGui.roundRadius - padding,
                color = GuiSettings.mainColor.multAlpha(0.3),
                shade = true
            )

            if (ClickGui.outline) {
                outlineRect(
                    rect = rect,
                    roundRadius = ClickGui.roundRadius,
                    color = (if (GuiSettings.shadeBackground) Color.WHITE else primaryColor).multAlpha(activeAnimation),
                    glowRadius = ClickGui.outlineWidth * activeAnimation,
                    shade = true
                )
            }
        }*/
    }
}
