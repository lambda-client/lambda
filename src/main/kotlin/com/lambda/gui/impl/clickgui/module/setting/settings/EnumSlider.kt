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

import com.lambda.config.settings.comparable.EnumSetting
import com.lambda.config.settings.comparable.EnumSetting.Companion.enumValues
import com.lambda.gui.component.core.UIBuilder
import com.lambda.gui.component.layout.Layout
import com.lambda.gui.impl.clickgui.module.setting.SettingSlider
import com.lambda.util.NamedEnum
import com.lambda.util.math.MathUtils.floorToInt
import com.lambda.util.math.transform
import kotlin.reflect.KMutableProperty0

class EnumSlider <T : Enum<T>>(
    owner: Layout,
    name: String,
    field: KMutableProperty0<T>
) : SettingSlider<T>(owner, name, field) {
    override val settingValue: String
        get() = (settingDelegate as? NamedEnum)?.displayName ?: settingDelegate.name

    init {
        slider.progress {
            transform(
                value = settingDelegate.ordinal.toDouble(),
                ogStart = 0.0, ogEnd = settingDelegate.enumValues.lastIndex.toDouble(),
                nStart = 0.0, nEnd = 1.0
            )
        }

        slider.onSlide {
            settingDelegate = settingDelegate.enumValues.let { entries ->
                entries[(it * entries.size)
                    .floorToInt()
                    .coerceIn(0, entries.size - 1)]
            }
        }
    }

    companion object {
        /**
         * Creates an [EnumSlider] - visual representation of the [EnumSetting]
         */
        @UIBuilder
        @Suppress("UNCHECKED_CAST")
        fun <T: Enum<T>> Layout.enumSetting(
            name: String,
            field: KMutableProperty0<Enum<*>>
        ) = EnumSlider(this, name, field as KMutableProperty0<T>).apply(children::add)
    }
}
