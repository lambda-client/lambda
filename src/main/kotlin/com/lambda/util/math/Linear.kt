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

import com.lambda.interaction.request.rotation.Rotation
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import java.awt.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random.Default.nextDouble

/**
 * Iterates over the double range with the specified step.
 */
fun ClosedRange<Double>.step(step: Double) = object : DoubleIterator() {
    private var next = start
    override fun hasNext() = next <= endInclusive
    override fun nextDouble() = next.also { next += step }
}

/**
 * Iterates over the float range with the specified step.
 */
fun ClosedRange<Float>.step(step: Float) = object : FloatIterator() {
    private var next = start
    override fun hasNext() = next <= endInclusive
    override fun nextFloat() = next.also { next += step }
}

/**
 * Returns a random number within the range.
 */
fun ClosedRange<Double>.random() = nextDouble(start, endInclusive)

/**
 * Converts a value from one range to a normalized value between 0 and 1.
 */
fun ClosedRange<Double>.normalize(value: Double): Double =
    transform(value, 0.0, 1.0)

/**
 * Converts a value from one range to a normalized value between 0 and 1.
 */
fun ClosedRange<Float>.normalize(value: Float): Float =
    transform(value, 0f, 1f)

/**
 * Inverts the range.
 */
fun ClosedRange<Float>.inv() = endInclusive to start

/**
 * Converts a value from one range to another while keeping the ratio using linear interpolation.
 *
 * @param value The value to convert.
 * @param min The minimum of the new range.
 * @param max The maximum of the new range.
 *
 * @return The converted value.
 */
fun ClosedRange<Double>.transform(
    value: Double,
    min: Double,
    max: Double,
): Double =
    transform(value, start, endInclusive, min, max)

/**
 * Converts a value from one range to another while keeping the ratio using linear interpolation.
 *
 * @param value The value to convert.
 * @param min The minimum of the new range.
 * @param max The maximum of the new range.
 *
 * @return The converted value.
 */
fun ClosedRange<Float>.transform(
    value: Float,
    min: Float,
    max: Float,
): Float =
    transform(value, start, endInclusive, min, max)

/**
 * Linear interpolation between two axes-aligned boxes.
 *
 * @param start The start box.
 * @param end The end box.
 */
fun lerp(value: Double, start: Box, end: Box) =
    Box(
        lerp(value, start.minX, end.minX),
        lerp(value, start.minY, end.minY),
        lerp(value, start.minZ, end.minZ),
        lerp(value, start.maxX, end.maxX),
        lerp(value, start.maxY, end.maxY),
        lerp(value, start.maxZ, end.maxZ),
    )

/**
 * Linear interpolation between two 2d vectors.
 *
 * @param start The start vector.
 * @param end The end vector.
 */
fun lerp(value: Double, start: Vec2d, end: Vec2d) =
    Vec2d(
        lerp(value, start.x, end.x),
        lerp(value, start.y, end.y),
    )

/**
 * Linear interpolation between two 3d vectors.
 */
fun lerp(value: Double, start: Vec3d, end: Vec3d) =
    Vec3d(
        lerp(value, start.x, end.x),
        lerp(value, start.y, end.y),
        lerp(value, start.z, end.z),
    )

/**
 * Linear interpolation between two rotations.
 */
fun lerp(value: Double, start: Rotation, end: Rotation) =
    Rotation(
        lerp(value, start.yaw, end.yaw),
        lerp(value, start.pitch, end.pitch),
    )

/**
 * Linear interpolation between two rectangles
 */
fun lerp(value: Double, start: Rect, end: Rect) =
    Rect(
        lerp(value, start.leftTop, end.leftTop),
        lerp(value, start.rightBottom, end.rightBottom),
    )

/**
 * Linear interpolation between two colors.
 */
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
 *
 * @param start The start value.
 * @param end The end value.
 * @param value The interpolation factor, typically between 0 (representing [start]) and 1 (representing [end]).
 * @return The interpolated value between [start] and [end].
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
 *
 * @param start The start value.
 * @param end The end value.
 * @param value The interpolation factor, typically between 0 (representing [start]) and 1 (representing [end]).
 *
 * @return The interpolated value between [start] and [end].
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
 * @return The converted value.
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
 * @return The converted value.
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


/**
 * Coerces a value to be within the range.
 */
fun ClosedRange<Double>.coerceIn(value: Double) = value.coerceIn(start, endInclusive)

/**
 * Coerces a value to be within the range.
 */
fun ClosedRange<Float>.coerceIn(value: Float) = value.coerceIn(start, endInclusive)

/**
 * Coerces a value to be within a 2d vector.
 *
 * @param minX The minimum x value.
 * @param maxX The maximum x value.
 * @param minY The minimum y value.
 * @param maxY The maximum y value.
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
