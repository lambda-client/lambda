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

package com.lambda.gui.impl.clickgui.module.settings

import com.lambda.config.AbstractSetting
import com.lambda.graphics.animation.Animation.Companion.exp
import com.lambda.gui.component.HAlign
import com.lambda.gui.component.VAlign
import com.lambda.gui.component.core.TextField.Companion.textField
import com.lambda.gui.component.layout.Layout
import com.lambda.gui.impl.clickgui.core.SliderLayout.Companion.sliderBehind
import com.lambda.gui.impl.clickgui.module.SettingLayout
import com.lambda.module.modules.client.ClickGui
import com.lambda.util.math.lerp

abstract class SettingSlider <V : Any, T: AbstractSetting<V>>(
    owner: Layout, setting: T
) : SettingLayout<V, T>(owner, setting, false) {
    private var changeAnimation by animation.exp(0.0, 1.0, 0.5) { true }

    private val sliderHeight = 3.0

    protected val slider = sliderBehind(titleBar) {
        val sl = this@SettingSlider

        onUpdate {
            positionX = sl.positionX
            positionY = sl.positionY + sl.height * 0.75 - sliderHeight * 0.5
            width = sl.width
            height = sliderHeight
        }
    }

    init {
        titleBar.use {
            onUpdate {
                height = ClickGui.settingsHeight * 1.25
            }

            textField.onUpdate {
                offsetY = 0.25 * height
                textVAlignment = VAlign.TOP
            }

            textField {
                var lastValue: String

                onUpdate {
                    lastValue = text
                    text = settingDelegate.toString()
                    if (lastValue != text) changeAnimation = 0.0

                    offsetX = textField.offsetX
                    offsetY = textField.offsetY

                    textHAlignment = HAlign.RIGHT
                    textVAlignment = textField.textVAlignment
                    shadow = textField.shadow
                    color = textField.color

                    scale = textField.scale * lerp(changeAnimation, 1.1, 1.0)
                }
            }
        }
    }
}