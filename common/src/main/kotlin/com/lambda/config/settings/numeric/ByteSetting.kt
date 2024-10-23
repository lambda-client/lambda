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

/**
 * Represents a [NumericSetting] for [Byte] values with a specific [range] and [step].
 *
 * The [value] of the setting is coerced into the specified [range] and rounded to the nearest [step].
 * The [visibility] and [description] of the setting are inherited from [NumericSetting].
 *
 * @property name The name of the setting.
 * @property range The range within which the setting's [value] must fall.
 * @property step The [step] to which the setting's [value] is rounded.
 * @property visibility A function that determines whether the setting [isVisible].
 * @property description A [description] of the setting.
 * @property unit The unit of the setting's [value].
 */
class ByteSetting(
    override val name: String,
    defaultValue: Byte,
    override val range: ClosedRange<Byte>,
    override val step: Byte,
    description: String,
    visibility: () -> Boolean,
    unit: String,
) : NumericSetting<Byte>(
    defaultValue,
    range,
    step,
    description,
    visibility,
    unit
)
