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

package com.lambda.config.settings

import com.google.gson.reflect.TypeToken
import com.lambda.config.AbstractSetting
import java.text.NumberFormat
import java.util.*
import kotlin.reflect.KProperty

/**
 * Represents a [NumericSetting] with a specific [range] and [step].
 *
 * The [value] of the setting is coerced into the specified [range] and rounded to the nearest [step].
 * The [visibility] and [description] of the setting are inherited from [AbstractSetting].
 *
 * @property range The range within which the setting's [value] must fall.
 * @property step The [step] to which the setting's [value] is rounded.
 * @property visibility A function that determines whether the setting [isVisible].
 * @property description A [description] of the setting.
 * @property unit The unit of the setting's [value].
 */
abstract class NumericSetting<T>(
    value: T,
    open val range: ClosedRange<T>,
    open val step: T,
    description: String,
    visibility: () -> Boolean,
    val unit: String,
) : AbstractSetting<T>(
    value,
    TypeToken.get(value::class.java).type,
    description,
    visibility
) where T : Number, T : Comparable<T> {
    private val formatter = NumberFormat.getNumberInstance(Locale.getDefault())

    override fun toString() = "${formatter.format(value)}$unit"

    override operator fun setValue(thisRef: Any?, property: KProperty<*>, valueIn: T) {
        value = valueIn.coerceIn(range)
    }
}
