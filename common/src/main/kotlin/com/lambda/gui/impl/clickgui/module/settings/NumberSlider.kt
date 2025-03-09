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

import com.lambda.config.settings.NumericSetting
import com.lambda.gui.component.core.UIBuilder
import com.lambda.gui.component.layout.Layout
import com.lambda.util.math.MathUtils.roundToStep
import com.lambda.util.math.MathUtils.typeConvert
import com.lambda.util.math.lerp
import com.lambda.util.math.transform

class NumberSlider <V> (
    owner: Layout, setting: NumericSetting<V>
) : SettingSlider<V, NumericSetting<V>>(owner, setting) where V : Number, V : Comparable<V> {
    private val min = setting.range.start.toDouble()
    private val max = setting.range.endInclusive.toDouble()

    init {
        slider.progress {
            transform(
                settingDelegate.toDouble(),
                min, max,
                0.0, 1.0
            )
        }

        slider.onSlide {
            settingDelegate = settingDelegate.typeConvert(
                lerp(it, min, max).roundToStep(setting.step.toDouble())
            )
        }
    }

    companion object {
        /**
         * Creates an [NumberSlider] - visual representation of the [NumericSetting]
         */
        @UIBuilder
        fun <T> Layout.numericSetting(setting: NumericSetting<T>) where T : Number, T : Comparable<T> =
            NumberSlider(this, setting).apply(children::add)
    }
}