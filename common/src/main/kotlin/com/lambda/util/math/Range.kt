package com.lambda.util.math

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
    scale(value, 0.0, 1.0)

/**
 * Converts a value from one range to a normalized value between 0 and 1.
 */
fun ClosedRange<Float>.normalize(value: Float): Float =
    scale(value, 0.0f, 1.0f)

/**
 * Inverts the range.
 */
fun ClosedRange<Float>.inverted() = endInclusive to start

/**
 * Sinusoidal interpolation between two values.
 */
fun ClosedRange<Double>.sinInterpolate(value: Double): Double =
    transform(value, start, endInclusive, -1.0, 1.0)

/**
 * Sinusoidal interpolation between two values.
 */
fun ClosedRange<Float>.sinInterpolate(value: Float): Float =
    transform(value, start, endInclusive, -1.0f, 1.0f)

/**
 * Converts a value from one range to another while keeping the ratio using linear interpolation.
 * @param value The value to convert.
 * @param minIn The minimum of the new range.
 * @param maxIn The maximum of the new range.
 * @return The converted value.
 */
fun ClosedRange<Double>.scale(value: Double, minIn: Double, maxIn: Double): Double =
    transform(value, start, endInclusive, minIn, maxIn)

/**
 * Converts a value from one range to another while keeping the ratio using linear interpolation.
 * @param value The value to convert.
 * @param minIn The minimum of the new range.
 * @param maxIn The maximum of the new range.
 * @return The converted value.
 */
fun ClosedRange<Float>.scale(value: Float, minIn: Float, maxIn: Float): Float =
    transform(value, start, endInclusive, minIn, maxIn)

/**
 * Converts a value from one range to another while keeping the ratio using linear interpolation.
 * @param value The value to convert.
 * @param x1 The minimum of the old range.
 * @param y1 The maximum of the old range.
 * @param x2 The minimum of the new range.
 * @param y2 The maximum of the new range.
 * @return The converted value.
 * @see <a href="https://en.wikipedia.org/wiki/Linear_interpolation">Linear Interpolation</a>
 */
fun transform(value: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double =
    (x2 + (value - x1) * ((y2 - x2) / (y1 - x1)))

/**
 * Converts a value from one range to another while keeping the ratio using linear interpolation.
 * @param value The value to convert.
 * @param x1 The minimum of the old range.
 * @param y1 The maximum of the old range.
 * @param x2 The minimum of the new range.
 * @param y2 The maximum of the new range.
 * @return The converted value.
 * @see <a href="https://en.wikipedia.org/wiki/Linear_interpolation">Linear Interpolation</a>
 */
fun transform(value: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float =
    (x2 + (value - x1) * ((y2 - x2) / (y1 - x1)))


/**
 * Coerces a value to be within the range.
 * @param value The value to clamp.
 * @return The clamped value.
 */
fun ClosedRange<Double>.coerceIn(value: Double) = value.coerceIn(start, endInclusive)
