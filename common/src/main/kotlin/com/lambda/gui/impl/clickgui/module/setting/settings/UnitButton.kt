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

import com.lambda.gui.component.core.UIBuilder
import com.lambda.gui.component.layout.Layout
import com.lambda.gui.impl.clickgui.module.setting.SettingLayout
import com.lambda.util.Mouse
import kotlin.reflect.KMutableProperty0

class UnitButton <T> (
    owner: Layout,
    name: String,
    field: KMutableProperty0<() -> T>,
) : SettingLayout<() -> T>(owner, name, field) {
    init {
        onMouseAction(Mouse.Button.Left) {
            settingDelegate()
        }
    }

    companion object {
        /**
         * Creates a [UnitButton]
         */
        @UIBuilder
        fun <T> Layout.unitButton(name: String, field: KMutableProperty0<() -> T>) =
            UnitButton(this, name, field).apply(children::add)

        /**
         * Creates a [UnitButton]
         */
        @UIBuilder
        fun <T> Layout.unitButton(name: String, block: () -> T) =
            UnitButton(this, name, CapturedUnit(block)::block).apply(children::add)

        private class CapturedUnit <T> (
            var block: () -> T
        )
    }
}
