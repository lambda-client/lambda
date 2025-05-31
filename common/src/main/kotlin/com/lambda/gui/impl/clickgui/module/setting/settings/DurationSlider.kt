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
import com.lambda.gui.impl.clickgui.module.setting.SettingSlider
import com.lambda.util.math.MathUtils.roundToStep
import com.lambda.util.math.lerp
import com.lambda.util.math.transform
import kotlin.reflect.KMutableProperty0
import kotlin.time.Duration
import com.lambda.config.settings.comparable.DurationSetting
import com.lambda.util.extension.highestUnit
import kotlin.time.toDuration

class DurationSlider(
    owner: Layout,
    name: String,
    min: Duration,
    max: Duration,
    step: Duration,
    field: KMutableProperty0<Duration>,
) : SettingSlider<Duration>(owner, name, field) {
    override val settingValue: String
        get() = settingDelegate.toString()

    private val unit = step.highestUnit

    private val minNanos = min.toDouble(unit)
    private val maxNanos = max.toDouble(unit)
    private val stepNanos = step.toDouble(unit)

    init {
        // Slider logic
        slider.progress {
            transform(
                settingDelegate.toDouble(unit),
                minNanos,
                maxNanos,
                0.0,
                1.0,
            )
        }

        slider.onSlide {
            settingDelegate = lerp(it, minNanos, maxNanos)
                .roundToStep(stepNanos)
                .toDuration(unit)
                .coerceIn(min..max)
        }
    }

    companion object {
        /**
         * Creates an [DurationSlider] - visual representation of the [DurationSetting]
         */
        @UIBuilder
        fun Layout.durationSlider(
            name: String,
            min: Duration,
            max: Duration,
            step: Duration,
            field: KMutableProperty0<Duration>
        ) = DurationSlider(this, name, min, max, step, field).apply(children::add)
    }
}
