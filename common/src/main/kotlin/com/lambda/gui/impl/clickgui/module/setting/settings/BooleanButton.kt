/*
 * Copyright 2025 Lambda
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

package com.lambda.gui.impl.clickgui.module.setting.settings

import com.lambda.graphics.animation.Animation.Companion.exp
import com.lambda.module.modules.client.ClickGui
import com.lambda.gui.component.core.FilledRect.Companion.rect
import com.lambda.gui.component.core.GlowRect.Companion.glow
import com.lambda.gui.component.core.UIBuilder
import com.lambda.gui.component.layout.Layout
import com.lambda.gui.impl.clickgui.module.setting.SettingLayout
import com.lambda.util.Mouse
import com.lambda.util.math.Rect
import com.lambda.util.math.Vec2d
import com.lambda.util.math.lerp
import com.lambda.util.math.setAlpha
import java.awt.Color
import kotlin.reflect.KMutableProperty0

class BooleanButton(
    owner: Layout,
    name: String,
    field: KMutableProperty0<Boolean>
) : SettingLayout<Boolean>(owner, name, field) {
    private val activeAnimation by animation.exp(0.0, 1.0, 0.6, ::settingDelegate)

    init {
        val checkBox = rect { // Checkbox
            val shrink = 3.0
            setRadius(100.0)

            onUpdate {
                val rb = this@BooleanButton.rightBottom
                val h = this@BooleanButton.height

                rect = Rect(rb - Vec2d(h * 1.65, h), rb)
                    .shrink(shrink + (1.0 - showAnimation)) +
                        Vec2d.RIGHT * lerp(showAnimation, 5.0, -ClickGui.fontOffset + shrink)

                setColor(lerp(activeAnimation, Color.BLACK, Color.WHITE).setAlpha(0.25 * showAnimation))
                shade = ClickGui.backgroundShade
            }

            onTick {
                cursorController.setCursor(
                    if (isHovered) Mouse.Cursor.Pointer else Mouse.Cursor.Arrow
                )
            }
        }

        rect { // Knob
            setRadius(100.0)

            onUpdate {
                val knobStart = Rect.basedOn(checkBox.leftTop, Vec2d.ONE * checkBox.size.y)
                val knobEnd = Rect(checkBox.rightBottom - checkBox.size.y, checkBox.rightBottom)

                rect = lerp(
                    lerp(showAnimation, 1.0 - activeAnimation, activeAnimation),
                    knobStart,
                    knobEnd
                ).shrink(1.0)

                shade = ClickGui.backgroundShade

                setColor(lerp(activeAnimation, Color.WHITE, Color.BLACK).setAlpha(lerp(activeAnimation, 0.25, 0.4) * showAnimation))
            }
        }

        glow {
            onUpdate {
                rect = checkBox.rect
                setRadius(100.0)
                outerSpread = 5.0
                setColor(Color.BLACK.setAlpha(0.1 * showAnimation))
            }
        }

        onMouseAction(Mouse.Button.Left) {
            settingDelegate = !settingDelegate
        }
    }

    companion object {
        /**
         * Creates a [BooleanButton]
         */
        @UIBuilder
        fun Layout.booleanSetting(name: String, field: KMutableProperty0<Boolean>) =
            BooleanButton(this, name, field).apply(children::add)
    }
}
