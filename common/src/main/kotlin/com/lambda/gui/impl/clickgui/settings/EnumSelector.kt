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

package com.lambda.gui.impl.clickgui.settings

import com.lambda.config.settings.comparable.EnumSetting
import com.lambda.gui.component.HAlign
import com.lambda.gui.component.core.TextField.Companion.textField
import com.lambda.gui.component.core.UIBuilder
import com.lambda.gui.component.layout.Layout
import com.lambda.gui.impl.clickgui.SettingLayout
import com.lambda.util.Mouse

// ToDO: complete or transform to a slider
class EnumSelector <T : Enum<T>>(
    owner: Layout,
    setting: EnumSetting<T>
) : SettingLayout<T, EnumSetting<T>>(owner, setting, true) {

    init {
        setting.enumValues.forEach { enumEntry ->
            content.layout {
                val base = this@EnumSelector

                overrideSize(base::width) {
                    base.titleBar.height * 0.8
                }

                textField {
                    text = enumEntry.name
                    textHAlignment = HAlign.CENTER

                    onUpdate {
                        scale = base.titleBar.textField.scale
                        color = base.titleBar.textField.color
                    }
                }

                onMouseClick(Mouse.Button.Left, Mouse.Action.Click) {
                    base.settingDelegate = enumEntry
                }
            }
        }

        content.listify()
    }

    companion object {
        /**
         * Creates an [EnumSelector] - visual representation of the [EnumSetting]
         */
        @UIBuilder
        fun <T: Enum<T>> Layout.enumSetting(setting: EnumSetting<T>) =
            EnumSelector(this, setting).apply(children::add)
    }
}