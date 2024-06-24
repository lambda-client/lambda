package com.lambda.util.math

import com.lambda.interaction.rotation.Rotation
import com.lambda.util.math.ColorUtils.a
import com.lambda.util.math.ColorUtils.b
import com.lambda.util.math.ColorUtils.g
import com.lambda.util.math.ColorUtils.r
import net.minecraft.util.math.Vec3d
import java.awt.Color
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.*
import kotlin.random.Random.Default.nextDouble

object MathUtils {
    private const val PI_FLOAT = 3.141593f

    fun Float.toRadian() = this / 180.0f * PI_FLOAT

    fun Double.toRadian() = this / 180.0 * PI

    fun Float.toDegree() = this * 180.0f / PI_FLOAT

    fun Double.toDegree() = this * 180.0 / PI

    fun Boolean.toInt() = if (this) 1 else 0

    fun Boolean.toIntSign() = if (this) 1 else -1

    fun Double.floorToInt() = floor(this).toInt()

    fun Double.ceilToInt() = ceil(this).toInt()

    fun <T : Number> T.roundToStep(step: T): T {
        val stepD = step.toDouble()
        var value = round(toDouble() / stepD) * stepD
        value = value.roundToPlaces(decimalPlaces(stepD))
        if (abs(value) == 0.0) value = 0.0

        return typeConvert(value)
    }

    fun Double.roundToPlaces(places: Int) =
        BigDecimal(this).setScale(places, RoundingMode.HALF_EVEN).toDouble()

    fun <T : Number> T.typeConvert(valueIn: Double): T {
        @Suppress("UNCHECKED_CAST")
        return when (this) {
            is Byte -> valueIn.toInt().toByte()
            is Short -> valueIn.toInt().toShort()
            is Double -> valueIn
            is Float -> valueIn.toFloat()
            is Int -> valueIn.toInt()
            is Long -> valueIn.toLong()
            else -> throw IllegalArgumentException("Unsupported number type")
        } as T
    }

    private fun decimalPlaces(value: Double) = BigDecimal.valueOf(value).scale()

    fun random(v1: Double, v2: Double): Double {
        if (v1 == v2) return v1
        val min = min(v1, v2)
        val max = max(v1, v2)
        return nextDouble(min, max)
    }

    /**
     * @return The smallest power of two that is greater than or equal to the input integer.
     */
    fun Int.ceilToPOT(): Int {
        var i = this
        i--
        i = i or (i shr 1)
        i = i or (i shr 2)
        i = i or (i shr 4)
        i = i or (i shr 8)
        i = i or (i shr 16)
        return ++i
    }

    inline val Int.sq: Int get() = this * this

    /**
     * Performs linear interpolation between two Float values.
     *
     * This function calculates the value at a specific point
     * between [start] and [end] based on the interpolation factor [factor].
     * The interpolation factor [factor] is clamped between zero
     * and one to ensure the result stays within the range of [start] and [end].
     *
     * @param start The start value.
     * @param end The end value.
     * @param factor The interpolation factor, typically between 0 (representing [start]) and 1 (representing [end]).
     * @return The interpolated value between [start] and [end].
     */
    fun lerp(start: Float, end: Float, factor: Float) =
        start + ((end - start) * factor.coerceIn(0f, 1f))

    /**
     * Performs linear interpolation between two Double values.
     *
     * This function calculates the value at a specific point
     * between [start] and [end] based on the interpolation factor [factor].
     * The interpolation factor [factor] is clamped between zero
     * and one to ensure the result stays within the range of [start] and [end].
     *
     * @param start The start value.
     * @param end The end value.
     * @param factor The interpolation factor, typically between 0 (representing [start]) and 1 (representing [end]).
     * @return The interpolated value between [start] and [end].
     */
    fun lerp(start: Double, end: Double, factor: Double) =
        start + ((end - start) * factor.coerceIn(0.0, 1.0))

    fun lerp(start: Vec3d, end: Vec3d, factor: Double) =
        Vec3d(
            lerp(start.x, end.x, factor),
            lerp(start.y, end.y, factor),
            lerp(start.z, end.z, factor)
        )

    fun lerp(start: Vec2d, end: Vec2d, factor: Double) =
        Vec2d(
            lerp(start.x, end.x, factor),
            lerp(start.y, end.y, factor)
        )

    fun lerp(start: Rect, end: Rect, factor: Double) =
        Rect(
            lerp(start.leftTop, end.leftTop, factor),
            lerp(start.rightBottom, end.rightBottom, factor)
        )

    fun lerp(start: Rotation, end: Rotation, factor: Double) =
        Rotation(
            lerp(start.yaw, end.yaw, factor),
            lerp(start.pitch, end.pitch, factor)
        )

    fun lerp(c1: Color, c2: Color, p: Double) =
        Color(
            lerp(c1.r, c2.r, p).toFloat(),
            lerp(c1.g, c2.g, p).toFloat(),
            lerp(c1.b, c2.b, p).toFloat(),
            lerp(c1.a, c2.a, p).toFloat()
        )

    fun Vec2d.coerceIn(minX: Double, maxX: Double, minY: Double, maxY: Double) =
        Vec2d(
            max(minX, min(x, maxX)),
            max(minY, min(y, maxY))
        )
}
