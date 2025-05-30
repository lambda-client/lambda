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

package com.lambda.config.settings

import com.lambda.util.extension.highestUnit
import com.lambda.util.extension.symbol
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class DurationSetting(
    override val name: String,
    private val defaultValue: Duration,
    override val range: ClosedRange<Duration>,
    override val step: Duration = 1.toDuration(defaultValue.highestUnit), // FixMe: Causes issues if the lower bound of the range is less than the step duration unit
                                                                          //    Ex: 60.microseconds..10.minutes will have a step of 1 minute
                                                                          //    Maybe we should leave this as is for the default behavior
    description: String,
    visibility: () -> Boolean,
) : NumericSetting<Duration>(
    defaultValue,
    range,
    step,
    description,
    visibility,
    "",
) {
    // ToDo:
    //  Should we determine the unit from the step since this is the in/decrement value within the range so it would make sense to use that for the unit inference
    override fun toString() = "${value.toInt(value.highestUnit)} ${value.highestUnit.symbol}"
}
