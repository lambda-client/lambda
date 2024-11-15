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

package com.lambda.util

import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object Formatting {
    val Vec3d.string: String
        get() = asString()

    fun Vec3d.asString(decimals: Int = 2): String {
        val format = "%.${decimals}f"
        return "(${format.format(x)}, ${format.format(y)}, ${format.format(z)})"
    }

    val BlockPos.string: String
        get() = "X: ${x} Y: ${y} Z: ${z}"

    fun getTime(formatter: DateTimeFormatter = DateTimeFormatter.RFC_1123_DATE_TIME): String {
        val localDateTime = LocalDateTime.now()
        val zoneId = ZoneId.systemDefault()
        val zonedDateTime = ZonedDateTime.of(localDateTime, zoneId)

        return zonedDateTime.format(formatter)
    }
}
