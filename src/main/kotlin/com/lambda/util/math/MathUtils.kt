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

package com.lambda.util.math

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random.Default.nextDouble

object MathUtils {
	private const val PI_FLOAT = 3.141593f

	inline val Int.sq: Int get() = this * this
	inline val Float.sq: Float get() = this * this
	inline val Double.sq: Double get() = this * this

	fun Float.toRadian() = this / 180.0f * PI_FLOAT
	fun Double.toRadian() = this / 180.0 * PI

	fun Float.toDegree() = this * 180.0f / PI_FLOAT
	fun Double.toDegree() = this * 180.0 / PI

	fun Boolean.toInt() = if (this) 1 else 0
	fun Boolean.toIntSign() = if (this) 1 else -1
	fun Boolean.toFloat() = if (this) 1f else 0f
	fun Boolean.toFloatSign() = if (this) 1f else -1f
	fun Boolean.toDouble() = if (this) 1.0 else 0.0
	fun Boolean.toDoubleSign() = if (this) 1.0 else -1.0

	fun Double.floorToInt() = floor(this).toInt()
	fun Double.ceilToInt() = ceil(this).toInt()
	fun Int.logCap(minimum: Int) = max(minimum.toDouble(), ceil(log2(toDouble()))).toInt()

	fun <T : Number> T.roundToStep(step: T): T {
		val valueBD = BigDecimal(toString())
		val stepBD = BigDecimal(step.toString())
		if (stepBD.compareTo(BigDecimal.ZERO) == 0) return this
		val scaled = valueBD.divide(stepBD, stepBD.scale(), RoundingMode.HALF_UP)
			.setScale(0, RoundingMode.HALF_UP)
			.multiply(stepBD)
			.setScale(stepBD.scale(), RoundingMode.HALF_UP)

		return typeConvert(scaled.toDouble())
	}

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

	fun Vec2d.roundToStep(step: Double) =
		Vec2d(x.roundToStep(step), y.roundToStep(step))

	fun random(v1: Double, v2: Double): Double {
		if (v1 == v2) return v1
		val min = min(v1, v2)
		val max = max(v1, v2)
		return nextDouble(min, max)
	}
}
