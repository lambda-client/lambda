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

package com.lambda.util

import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.floor
import kotlin.math.pow

object Formatting {
    @Deprecated("Use asString with decimals and locale parameters instead", ReplaceWith("asString(decimals, locale)"))
    val Vec3d.string: String
        get() = asString()

    @Deprecated("Use asString with decimals and locale parameters instead", ReplaceWith("asString(decimals, locale)"))
    val Float.string: String
        get() = "%,.2f".format(Locale.US, this)

    @Deprecated("Use asString with decimals and locale parameters instead", ReplaceWith("asString(decimals, locale)"))
    val Double.string: String
        get() = "%,.2f".format(Locale.US, this)

    fun Double.asString(decimals: Int = 2, locale: Locale = Locale.US, numberGrouping: Boolean = true): String {
        val format = if (numberGrouping) "%,.${decimals}f" else "%.${decimals}f"
        val factor = 10.0.pow(decimals)
        val floored = floor(this * factor) / factor
        return format.format(locale, floored)
    }

    fun Vec3d.asString(decimals: Int = 2, locale: Locale = Locale.US, numberGrouping: Boolean = true): String {
        val format = if (numberGrouping) "%,.${decimals}f" else "%.${decimals}f"
        val vec = floorToDecimals(this, decimals)
        return "(${format.format(locale, vec.x)} ${format.format(locale, vec.y)} ${format.format(locale, vec.z)})"
    }

    private fun floorToDecimals(vec: Vec3d, decimals: Int): Vec3d {
        val factor = 10.0.pow(decimals)
        return Vec3d(
            floor(vec.x * factor) / factor,
            floor(vec.y * factor) / factor,
            floor(vec.z * factor) / factor
        )
    }

    fun BlockPos.asString(decimals: Int = 2, locale: Locale = Locale.US, numberGrouping: Boolean = true): String {
        val x = x.toDouble().asString(decimals, locale, numberGrouping)
        val y = y.toDouble().asString(decimals, locale, numberGrouping)
        val z = z.toDouble().asString(decimals, locale, numberGrouping)
        return "($x $y $z)"
    }

    fun getTime(formatter: DateTimeFormatter = DateTimeFormatter.RFC_1123_DATE_TIME): String {
        val localDateTime = LocalDateTime.now()
        val zoneId = ZoneId.systemDefault()
        val zonedDateTime = ZonedDateTime.of(localDateTime, zoneId)

        return zonedDateTime.format(formatter)
    }
}
