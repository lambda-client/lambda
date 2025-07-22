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
 * @see [com.lambda.config.Configurable]
 */
abstract class NumericSetting<T>(
    value: T,
    open val range: ClosedRange<T>,
    open val step: T,
    description: String,
    val unit: String,
    visibility: () -> Boolean
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
