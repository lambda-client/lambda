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

package com.lambda.config.settings.comparable

import com.google.gson.reflect.TypeToken
import com.lambda.config.AbstractSetting
import kotlin.reflect.KProperty
import kotlin.time.Duration

class DurationSetting(
    override val name: String,
    defaultValue: Duration,
    val range: ClosedRange<Duration>,
    val step: Duration,
    description: String,
    visibility: () -> Boolean,
) : AbstractSetting<Duration>(
    defaultValue,
    TypeToken.get(defaultValue::class.java).type,
    description,
    visibility,
) {
    override fun toString() = value.toString()

    override operator fun setValue(thisRef: Any?, property: KProperty<*>, valueIn: Duration) {
        value = valueIn.coerceIn(range)
    }
}
