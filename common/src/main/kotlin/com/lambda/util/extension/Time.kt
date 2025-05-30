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

package com.lambda.util.extension

import kotlin.ranges.contains
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

val Int.ticks: Duration get() = (this*50).toDuration(DurationUnit.MILLISECONDS)
val Long.ticks: Duration get() = (this*50).toDuration(DurationUnit.MILLISECONDS)
val Double.ticks: Duration get() = (this*50).toDuration(DurationUnit.MILLISECONDS)

/**
 * Returns the highest unit present in the duration's nano
 */
val Duration.highestUnit: DurationUnit
    get() = when {
        inWholeDays > 0 -> DurationUnit.DAYS
        inWholeHours in 1..24 -> DurationUnit.HOURS
        inWholeMinutes in 1..60 -> DurationUnit.MINUTES
        inWholeSeconds in 1..60 -> DurationUnit.SECONDS
        inWholeMilliseconds in 1..1000 -> DurationUnit.MILLISECONDS
        inWholeMicroseconds in 1..1000 -> DurationUnit.MICROSECONDS
        inWholeNanoseconds in 0..1000 -> DurationUnit.NANOSECONDS
        else -> DurationUnit.NANOSECONDS // We need to please the almighty compiler
    }

val DurationUnit.symbol: String
    get() = when (this) {
        DurationUnit.NANOSECONDS -> "ns"
        DurationUnit.MICROSECONDS -> "μs"
        DurationUnit.MILLISECONDS -> "ms"
        DurationUnit.SECONDS -> "s"
        DurationUnit.MINUTES -> "min"
        DurationUnit.HOURS -> "hr"
        DurationUnit.DAYS -> "d"
    }
