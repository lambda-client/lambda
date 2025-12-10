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

import com.lambda.interaction.managers.rotating.Rotation
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import java.awt.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

infix fun ClosedRange<Double>.step(step: Double) = object : DoubleIterator() {
    private var next = start
    override fun hasNext() = next <= endInclusive
    override fun nextDouble() = next.also { next += step }
}

infix fun ClosedRange<Float>.step(step: Float) = object : FloatIterator() {
    private var next = start
    override fun hasNext() = next <= endInclusive
    override fun nextFloat() = next.also { next += step }
}

@JvmName("randomDouble")
fun ClosedRange<Double>.random(random: Random = Random) = start + (endInclusive - start) * random.nextDouble()
@JvmName("randomFloat")
fun ClosedRange<Float>.random(random: Random = Random) = start + (endInclusive - start) * random.nextDouble()

fun ClosedRange<Double>.normalize(value: Double): Double = transform(value, 0.0, 1.0)
fun ClosedRange<Float>.normalize(value: Float): Float = transform(value, 0f, 1f)

@JvmName("invDouble")
fun ClosedRange<Double>.inv() = endInclusive to start
@JvmName("invFloat")
fun ClosedRange<Float>.inv() = endInclusive to start

/**
 * Converts a value from one range to another while keeping the ratio using linear interpolation.
 */
fun ClosedRange<Double>.transform(
    value: Double,
    min: Double,
    max: Double,
): Double =
    transform(value, start, endInclusive, min, max)

/**
 * Converts a value from one range to another while keeping the ratio using linear interpolation.
 */
fun ClosedRange<Float>.transform(
    value: Float,
    min: Float,
    max: Float,
): Float =
    transform(value, start, endInclusive, min, max)

fun lerp(value: Double, start: Box, end: Box) =
    Box(
        lerp(value, start.minX, end.minX),
        lerp(value, start.minY, end.minY),
        lerp(value, start.minZ, end.minZ),
        lerp(value, start.maxX, end.maxX),
        lerp(value, start.maxY, end.maxY),
        lerp(value, start.maxZ, end.maxZ),
    )

fun lerp(value: Double, start: Vec2d, end: Vec2d) =
    Vec2d(
        lerp(value, start.x, end.x),
        lerp(value, start.y, end.y),
    )

fun lerp(value: Double, start: Vec3d, end: Vec3d) =
    Vec3d(
        lerp(value, start.x, end.x),
        lerp(value, start.y, end.y),
        lerp(value, start.z, end.z),
    )

fun lerp(value: Double, start: Rotation, end: Rotation) =
    Rotation(
        lerp(value, start.yaw, end.yaw),
        lerp(value, start.pitch, end.pitch),
    )

fun lerp(value: Double, start: Rect, end: Rect) =
    Rect(
        lerp(value, start.leftTop, end.leftTop),
        lerp(value, start.rightBottom, end.rightBottom),
    )

fun lerp(value: Double, start: Color, end: Color) =
    Color(
        lerp(value, start.r, end.r).toFloat(),
        lerp(value, start.g, end.g).toFloat(),
        lerp(value, start.b, end.b).toFloat(),
        lerp(value, start.a, end.a).toFloat(),
    )

/**
 * Performs linear interpolation between two Double values.
 *
 * This function calculates the value at a specific point
 * between [start] and [end] based on the interpolation factor [value].
 * The interpolation factor [value] is clamped between zero
 * and one to ensure the result stays within the range of [start] and [end].
 */
fun lerp(value: Double, start: Double, end: Double) =
    transform(value.coerceIn(0.0, 1.0), 0.0, 1.0, start, end)

/**
 * Performs linear interpolation between two Float values.
 *
 * This function calculates the value at a specific point
 * between [start] and [end] based on the interpolation factor [value].
 * The interpolation factor [value] is clamped between zero
 * and one to ensure the result stays within the range of [start] and [end].
 */
fun lerp(value: Float, start: Float, end: Float) =
    transform(value.coerceIn(0f, 1f), 0f, 1f, start, end)

/**
 * Converts a value from one range to another while keeping the ratio using linear map.
 *
 * @param value The value to convert.
 * @param ogStart The original start value.
 * @param ogEnd The original end value.
 * @param nStart The new start value.
 * @param nEnd The new end value.
 *
 * @see <a href="https://en.wikipedia.org/wiki/Linear_map">Linear Map</a>
 */
fun transform(
    value: Double,
    ogStart: Double,
    ogEnd: Double,
    nStart: Double,
    nEnd: Double,
): Double =
    nStart + (value - ogStart) * ((nEnd - nStart) / (ogEnd - ogStart))

/**
 * Converts a value from one range to another while keeping the ratio using linear map.
 *
 * @param value The value to convert.
 * @param ogStart The original start value.
 * @param ogEnd The original end value.
 * @param nStart The new start value.
 * @param nEnd The new end value.
 *
 * @see <a href="https://en.wikipedia.org/wiki/Linear_map">Linear Map</a>
 */
fun transform(
    value: Float,
    ogStart: Float,
    ogEnd: Float,
    nStart: Float,
    nEnd: Float,
): Float =
    nStart + (value - ogStart) * ((nEnd - nStart) / (ogEnd - ogStart))


fun ClosedRange<Double>.coerceIn(value: Double) = value.coerceIn(start, endInclusive)
fun ClosedRange<Float>.coerceIn(value: Float) = value.coerceIn(start, endInclusive)

/**
 * Coerces a value to be within a 2d vector.
 */
fun Vec2d.coerceIn(
    minX: Double,
    maxX: Double,
    minY: Double,
    maxY: Double,
) =
    Vec2d(
        max(minX, min(x, maxX)),
        max(minY, min(y, maxY))
    )
