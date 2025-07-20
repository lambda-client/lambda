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

import com.lambda.config.settings.complex.KeyBindSetting
import com.lambda.gui.component.HAlign
import com.lambda.gui.component.core.TextField.Companion.textField
import com.lambda.gui.component.core.UIBuilder
import com.lambda.gui.component.layout.Layout
import com.lambda.gui.impl.clickgui.module.ModuleLayout
import com.lambda.gui.impl.clickgui.module.setting.SettingLayout
import com.lambda.util.KeyCode
import com.lambda.util.Mouse
import com.lambda.util.extension.displayValue
import kotlin.reflect.KMutableProperty0

class KeybindPicker(
    owner: Layout,
    name: String,
    field: KMutableProperty0<KeyCode>
) : SettingLayout<KeyCode>(owner, name, field) {
    private var isListening = false

    init {
        textField {
            onUpdate {
                mergeFrom(titleBar.textField)
                text = if (isListening) "..." else settingDelegate.displayValue
                textHAlignment = HAlign.RIGHT
            }
        }

        onShow {
            isListening = false
        }

        onTick {
            val module = (owner.owner as? ModuleLayout) ?: return@onTick
            if (module.isMinimized) isListening = false
        }

        onMouseAction(Mouse.Button.Left) {
            isListening = !isListening
        }

        onKeyPress { key ->
            if (!isListening) return@onKeyPress

            settingDelegate = key
            isListening = false
        }
    }

    companion object {
        /**
         * Creates a [KeybindPicker] - visual representation of the [KeyBindSetting]
         */
        @UIBuilder
        fun Layout.keybindSetting(name: String, field: KMutableProperty0<KeyCode>) =
            KeybindPicker(this, name, field).apply(children::add)
    }
}
