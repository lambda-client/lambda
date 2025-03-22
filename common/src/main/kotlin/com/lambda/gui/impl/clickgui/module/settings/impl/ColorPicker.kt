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

package com.lambda.gui.impl.clickgui.module.settings.impl

import com.lambda.gui.component.core.UIBuilder
import com.lambda.gui.component.layout.Layout
import com.lambda.gui.component.popup.Popup
import com.lambda.gui.impl.clickgui.module.settings.SettingLayout
import com.lambda.util.Mouse
import java.awt.Color
import kotlin.reflect.KMutableProperty0

class ColorPicker(
    owner: Layout,
    name: String,
    field: KMutableProperty0<Color>,
) : SettingLayout<Color>(owner, name, field) {
    private val popup = Popup(this, name).apply {
        window.use {
            windowWidth = 200.0
            windowHeight = 100.0
        }
    }

    init {
        onMouseAction(Mouse.Button.Left) {
            popup.show()
        }
    }

    companion object {
        /**
         * Creates a [ColorPicker]
         */
        @UIBuilder
        fun Layout.colorPicker(name: String, field: KMutableProperty0<Color>) =
            ColorPicker(this, name, field).apply(children::add)
    }
}