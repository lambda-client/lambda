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

package com.lambda.newgui.impl.clickgui.settings

import com.lambda.config.settings.comparable.BooleanSetting
import com.lambda.newgui.component.core.UIBuilder
import com.lambda.newgui.component.layout.Layout
import com.lambda.newgui.impl.clickgui.SettingLayout
import com.lambda.util.Mouse

class BooleanButton(
    owner: Layout,
    setting: BooleanSetting
) : SettingLayout<Boolean, BooleanSetting>(owner, setting) {
    init {
        titleBar.onMouseClick { button, action ->
            if (button == Mouse.Button.Left && action == Mouse.Action.Click) {
                setting.value = !setting.value
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
