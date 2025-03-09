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

import com.lambda.config.settings.FunctionSetting
import com.lambda.gui.component.core.UIBuilder
import com.lambda.gui.component.layout.Layout
import com.lambda.gui.impl.clickgui.SettingLayout
import com.lambda.util.Mouse

class UnitButton(
    owner: Layout,
    setting: FunctionSetting<Unit>,
) : SettingLayout<() -> Unit, FunctionSetting<Unit>>(owner, setting) {
    init {
        onMouseClick(Mouse.Button.Left, Mouse.Action.Click) {
            setting.value()
        }
    }

    companion object {
        /**
         * Creates a [UnitButton] - visual representation of the [FunctionSetting]
         */
        @UIBuilder
        fun Layout.unitSetting(setting: FunctionSetting<Unit>) =
            UnitButton(this, setting).apply(children::add)
    }
}
