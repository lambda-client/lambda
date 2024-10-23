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

package com.lambda.config.settings.numeric

import com.lambda.config.settings.NumericSetting

class FloatSetting(
    override val name: String,
    defaultValue: Float,
    override val range: ClosedRange<Float>,
    override val step: Float = 1f,
    description: String,
    visibility: () -> Boolean,
    unit: String,
) : NumericSetting<Float>(
    defaultValue,
    range,
    step,
    description,
    visibility,
    unit
)
