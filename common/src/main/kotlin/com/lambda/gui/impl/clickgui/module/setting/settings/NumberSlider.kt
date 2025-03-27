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

import com.lambda.config.settings.NumericSetting
import com.lambda.gui.component.core.UIBuilder
import com.lambda.gui.component.layout.Layout
import com.lambda.gui.impl.clickgui.module.setting.SettingSlider
import com.lambda.util.math.MathUtils.roundToStep
import com.lambda.util.math.MathUtils.typeConvert
import com.lambda.util.math.lerp
import com.lambda.util.math.transform
import kotlin.reflect.KMutableProperty0

class NumberSlider <V> (
    owner: Layout,
    name: String, private val unit: String,
    minV: V, maxV: V, stepV: V,
    field: KMutableProperty0<V>
) : SettingSlider<V>(owner, name, field) where V : Number, V : Comparable<V> {
    var min = minV.toDouble()
    var max = maxV.toDouble()
    var step = stepV.toDouble()
    var forceRoundDisplayValue = false

    override val settingValue: String
        get() = "${settingDelegate.let { 
            if (forceRoundDisplayValue) it.roundToStep(step) else it
        }}${unit}"

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
                lerp(it, min, max).roundToStep(step).toDouble()
            )
        }
    }

    companion object {
        /**
         * Creates an [NumberSlider] - visual representation of the [NumericSetting]
         */
        @UIBuilder
        fun <T> Layout.numberSlider(
            name: String, unit: String,
            minV: T, maxV: T, stepV: T,
            field: KMutableProperty0<T>
        ) where T : Number, T : Comparable<T> =
            NumberSlider(this, name, unit, minV, maxV, stepV, field).apply(children::add)
    }
}