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

import com.lambda.config.groups.FormatterConfig
import com.lambda.util.math.Vec2d
import net.minecraft.util.math.Vec2f
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.Vec3i
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

object Formatting {
    fun Short.format(formatter: FormatterConfig) = format(formatter.locale)
    fun Short.format(locale: Locale = Default.locale) = "%d".format(locale, this)

    fun Int.format(formatter: FormatterConfig) = format(formatter.locale)
    fun Int.format(locale: Locale = Default.locale) = "%d".format(locale, this)

    fun Long.format(formatter: FormatterConfig) = format(formatter.locale)
    fun Long.format(locale: Locale = Default.locale) = "%d".format(locale, this)

    fun Float.format(formatter: FormatterConfig) = format(formatter.locale, formatter.precision)
    fun Float.format(locale: Locale = Default.locale, precision: Int = Default.precision) = "%,.${precision}f".format(locale, this)

    fun Double.format(formatter: FormatterConfig) = format(formatter.locale, formatter.precision)
    fun Double.format(locale: Locale = Default.locale, precision: Int = Default.precision) = "%,.${precision}f".format(locale, this)

    fun Vec2f.format(formatter: FormatterConfig) = format(formatter.locale, formatter.separator, formatter.prefix, formatter.postfix, formatter.precision)
    fun Vec2f.format(locale: Locale = Default.locale, separator: String = Default.separator, prefix: String = Default.prefix, postfix: String = Default.postfix, precision: Int = Default.precision) =
        "$prefix${x.format(locale, precision)}$separator${y.format(locale, precision)}$postfix"

    fun Vec2d.format(formatter: FormatterConfig) = format(formatter.locale, formatter.separator, formatter.prefix, formatter.postfix, formatter.precision)
    fun Vec2d.format(locale: Locale = Default.locale, separator: String = Default.separator, prefix: String = Default.prefix, postfix: String = Default.postfix, precision: Int = Default.precision) =
        "$prefix${x.format(locale, precision)}$separator${y.format(locale, precision)}$postfix"

    fun Vec3i.format(formatter: FormatterConfig) = format(formatter.locale, formatter.separator, formatter.prefix, formatter.postfix)
    fun Vec3i.format(locale: Locale = Default.locale, separator: String = Default.separator, prefix: String = Default.prefix, postfix: String = Default.postfix) =
        "$prefix${x.format(locale)}$separator${y.format(locale)}$separator${z.format(locale)}$postfix"

    fun Vec3d.format(formatter: FormatterConfig) = format(formatter.locale, formatter.separator, formatter.prefix, formatter.postfix, formatter.precision)
    fun Vec3d.format(locale: Locale = Default.locale, separator: String = Default.separator, prefix: String = Default.prefix, postfix: String = Default.postfix, precision: Int = Default.precision) =
        "$prefix${x.format(locale, precision)}$separator${y.format(locale, precision)}$separator${z.format(locale, precision)}$postfix"

    fun ShortArray.format(formatter: FormatterConfig) = format(formatter.locale, formatter.separator, formatter.prefix, formatter.postfix)
    fun ShortArray.format(locale: Locale = Default.locale, separator: String = Default.separator, prefix: String = Default.prefix, postfix: String = Default.postfix) =
        joinToString(separator, prefix, postfix) { it.format(locale) }

    fun IntArray.format(formatter: FormatterConfig) = format(formatter.locale, formatter.separator, formatter.prefix, formatter.postfix)
    fun IntArray.format(locale: Locale = Default.locale, separator: String = Default.separator, prefix: String = Default.prefix, postfix: String = Default.postfix) =
        joinToString(separator, prefix, postfix) { it.format(locale) }

    fun LongArray.format(formatter: FormatterConfig) = format(formatter.locale, formatter.separator, formatter.prefix, formatter.postfix)
    fun LongArray.format(locale: Locale = Default.locale, separator: String = Default.separator, prefix: String = Default.prefix, postfix: String = Default.postfix) =
        joinToString(separator, prefix, postfix) { it.format(locale) }

    fun FloatArray.format(formatter: FormatterConfig) = format(formatter.locale, formatter.separator, formatter.prefix, formatter.postfix, formatter.precision)
    fun FloatArray.format(locale: Locale = Default.locale, separator: String = Default.separator, prefix: String = Default.prefix, postfix: String = Default.postfix, precision: Int = Default.precision) =
        joinToString(separator, prefix, postfix) { it.format(locale, precision) }

    fun DoubleArray.format(formatter: FormatterConfig) = format(formatter.locale, formatter.separator, formatter.prefix, formatter.postfix, formatter.precision)
    fun DoubleArray.format(locale: Locale = Default.locale, separator: String = Default.separator, prefix: String = Default.prefix, postfix: String = Default.postfix, precision: Int = Default.precision) =
        joinToString(separator, prefix, postfix) { it.format(locale, precision) }

    @JvmName("formatVec2fiList1")
    fun Iterable<Vec2f>.format(formatter: FormatterConfig) = format(formatter.locale, formatter.separator, formatter.prefix, formatter.postfix, formatter.precision)
    @JvmName("formatVec2fList2")
    fun Iterable<Vec2f>.format(locale: Locale = Default.locale, separator: String = Default.separator, prefix: String = Default.prefix, postfix: String = Default.postfix, precision: Int = Default.precision) =
        joinToString(separator, prefix, postfix) { it.format(locale, separator, prefix, postfix, precision) }

    @JvmName("formatVec2dList1")
    fun Iterable<Vec2d>.format(formatter: FormatterConfig) = format(formatter.locale, formatter.separator, formatter.prefix, formatter.postfix, formatter.precision)
    @JvmName("formatVec2dList2")
    fun Iterable<Vec2d>.format(locale: Locale = Default.locale, separator: String = Default.separator, prefix: String = Default.prefix, postfix: String = Default.postfix, precision: Int = Default.precision) =
        joinToString(separator, prefix, postfix) { it.format(locale, separator, prefix, postfix, precision) }

    @JvmName("formatVec3iList1")
    fun Iterable<Vec3i>.format(formatter: FormatterConfig) = format(formatter.locale, formatter.separator, formatter.prefix, formatter.postfix)
    @JvmName("formatVec3iList2")
    fun Iterable<Vec3i>.format(locale: Locale = Default.locale, separator: String = Default.separator, prefix: String = Default.prefix, postfix: String = Default.postfix) =
        joinToString(separator, prefix, postfix) { it.format(locale, separator, prefix, postfix) }

    @JvmName("formatVec3dList1")
    fun Iterable<Vec3d>.format(formatter: FormatterConfig) = format(formatter.locale, formatter.separator, formatter.prefix, formatter.postfix, formatter.precision)
    @JvmName("formatVec3dList2")
    fun Iterable<Vec3d>.format(locale: Locale = Default.locale, separator: String = Default.separator, prefix: String = Default.prefix, postfix: String = Default.postfix, precision: Int = Default.precision) =
        joinToString(separator, prefix, postfix) { it.format(locale, separator, prefix, postfix, precision) }

    fun LocalDate.format(formatter: FormatterConfig): String = format(formatter.format)
    fun LocalDate.format(format: DateTimeFormatter = Default.format): String = format(format)

    fun LocalDateTime.format(formatter: FormatterConfig): String = format(formatter.format)
    fun LocalDateTime.format(format: DateTimeFormatter = Default.format): String = format(format)

    fun ZonedDateTime.format(formatter: FormatterConfig): String = format(formatter.format)
    fun ZonedDateTime.format(format: DateTimeFormatter = Default.format): String = format(format)

    fun getTime(formatter: DateTimeFormatter = Default.format): String {
        val localDateTime = LocalDateTime.now()
        val zoneId = ZoneId.systemDefault()
        val zonedDateTime = ZonedDateTime.of(localDateTime, zoneId)

        return zonedDateTime.format(formatter)
    }

    object Default : FormatterConfig {
        override val locale: Locale = Locale.US
        override val separator: String = ","
        override val prefix: String = "("
        override val postfix: String = ")"
        override val precision: Int = 3
        override val format: DateTimeFormatter = DateTimeFormatter.ISO_DATE_TIME
    }
}
