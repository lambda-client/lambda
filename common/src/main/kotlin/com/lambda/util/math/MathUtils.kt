package com.lambda.util.math

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
        if (stepD == 0.0) return this

        var value = round(toDouble() / stepD) * stepD
        value = value.roundToPlaces(decimalPlaces(stepD))
        if (abs(value) == 0.0) value = 0.0

        return typeConvert(value)
    }

    fun Vec2d.roundToStep(step: Double): Vec2d =
        Vec2d(x.roundToStep(step), y.roundToStep(step))

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
}
