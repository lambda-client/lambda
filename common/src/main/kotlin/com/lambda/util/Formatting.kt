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