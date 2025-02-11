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

package com.lambda.gui.impl.clickgui.settings

import com.lambda.config.settings.comparable.BooleanSetting
import com.lambda.graphics.animation.Animation.Companion.exp
import com.lambda.module.modules.client.ClickGui
import com.lambda.gui.component.core.FilledRect.Companion.rect
import com.lambda.gui.component.core.UIBuilder
import com.lambda.gui.component.layout.Layout
import com.lambda.gui.impl.clickgui.SettingLayout
import com.lambda.util.Mouse
import com.lambda.util.math.Rect
import com.lambda.util.math.Vec2d
import com.lambda.util.math.lerp
import com.lambda.util.math.setAlpha
import java.awt.Color

class BooleanButton(
    owner: Layout,
    setting: BooleanSetting
) : SettingLayout<Boolean, BooleanSetting>(owner, setting) {
    private var activeAnimation by animation.exp(0.0, 1.0, 0.6, ::settingValue)

    init {
        val checkBox = rect { // Checkbox
            val shrink = 2.0
            setRadius(100.0)

            onUpdate {
                val rb = this@BooleanButton.rightBottom
                val h = this@BooleanButton.renderHeight

                rectangle = Rect(rb - Vec2d(h * 1.65, h), rb)
                    .shrink(shrink) + Vec2d.LEFT * (ClickGui.fontOffset - shrink)

                setColor(Color.BLACK.setAlpha(0.25 * visibilityAnimation))
                shade = ClickGui.backgroundShade
            }

            onTick {
                cursorController.setCursor(
                    if (isHovered) Mouse.Cursor.Pointer else Mouse.Cursor.Arrow
                )
            }

            onMouseClick { button, action ->
                if (button == Mouse.Button.Left && action == Mouse.Action.Click) {
                    setting.value = !setting.value
                }
            }
        }

        rect { // Knob
            setRadius(100.0)

            onUpdate {
                val knobStart = Rect.basedOn(checkBox.leftTop, Vec2d.ONE * checkBox.renderHeight)
                val knobEnd = Rect(checkBox.rightBottom - checkBox.renderHeight, checkBox.rightBottom)
                rectangle = lerp(activeAnimation, knobStart, knobEnd).shrink(1.0)
                shade = ClickGui.backgroundShade
                setColor(Color.WHITE.setAlpha(0.25 * visibilityAnimation))
            }
        }
    }

    companion object {
        /**
         * Creates a [BooleanButton] - visual representation of the [BooleanSetting]
         */
        @UIBuilder
        fun Layout.booleanSetting(setting: BooleanSetting) =
            BooleanButton(this, setting).apply(children::add)
    }
}
