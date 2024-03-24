package com.lambda.util.math

import net.minecraft.util.math.Box
import java.util.Random
import java.util.concurrent.ThreadLocalRandom
import kotlin.random.Random.Default.nextDouble

class DoubleRange(
    override val start: Double,
    override val endInclusive: Double
) : ClosedRange<Double> {
    infix fun step(step: Double): DoubleIterator {
        return object : DoubleIterator() {
            private var next = start
            override fun hasNext() = next <= endInclusive
            override fun nextDouble() = next.also { next += step }
        }
    }

    /**
     * Returns a random value within the range.
     */
    fun random() = nextDouble(start, endInclusive)

    /**
     * Returns a bounding box from two additional ranges.
     * @param y The second range.
     * @param z The third range.
     * @return The bounding box.
     */
    fun box(y: DoubleRange, z: DoubleRange) =
        Box(this.start, y.start, z.start, this.endInclusive, y.endInclusive, z.endInclusive)
}

infix fun Double.to(that: Double) = DoubleRange(this, that)

/**
 * Converts a value from one range to a normalized value between 0 and 1.
 */
fun <T> ClosedRange<T>.normalized(value: T): T where T : Comparable<T>, T : Number = scale(value, 0.0 as T, 1.0 as T) // hacky

/**
 * Inverts the range.
 */
fun <T> ClosedRange<T>.inverted(): ClosedRange<T> where T : Comparable<T>, T : Number = endInclusive..start

/**
 * Converts a value from one range to another while keeping the ratio using exponential interpolation.
 * @param value The value to convert.
 * @param minIn The minimum of the new range.
 * @param maxIn The maximum of the new range.
 * @return The converted value.
 */
fun <T> ClosedRange<T>.scale(value: T, minIn: T, maxIn: T): T where T : Comparable<T>, T : Number =
    transform(value, start, endInclusive, minIn, maxIn)

/**
 * Converts a value from one range to another while keeping the ratio using exponential interpolation.
 * @param value The value to convert.
 * @param x1 The minimum of the old range.
 * @param y1 The maximum of the old range.
 * @param x2 The minimum of the new range.
 * @param y2 The maximum of the new range.
 * @return The converted value.
 * @see <a href="https://en.wikipedia.org/wiki/Linear_interpolation">Linear Interpolation</a>
 */
fun <T> transform(value: T, x1: T, y1: T, x2: T, y2: T): T where T : Comparable<T>, T : Number =
    x2 + (value - x1) * ((y2 - x2) / (y1 - x1))


/**
 * Clamps a value to the range.
 * @param value The value to clamp.
 * @return The clamped value.
 */
fun <T : Comparable<T>> ClosedRange<T>.coerceIn(value: T) = value.coerceIn(start, endInclusive)
fun <T : Comparable<T>> T.coerceIn(range: ClosedRange<T>) = range.coerceIn(this)

private operator fun <T> T.minus(oldMin: T): T where T : Comparable<T>, T : Number {
    return (this.toDouble() - oldMin.toDouble()) as T
}

private operator fun <T> T.div(other: T): T where T : Comparable<T>, T : Number {
    return (this.toDouble() / other.toDouble()) as T
}

private operator fun <T> T.times(other: T): T where T : Comparable<T>, T : Number {
    return (this.toDouble() * other.toDouble()) as T
}

private operator fun <T> T.plus(other: T): T where T : Comparable<T>, T : Number {
    return (this.toDouble() + other.toDouble()) as T
}

fun <T> min(newMin: T, newMax: T): T where T : Comparable<T>, T : Number {
    return if (newMin < newMax) newMin else newMax
}

fun <T> max(newMin: T, newMax: T): T where T : Comparable<T>, T : Number {
    return if (newMin > newMax) newMin else newMax
}
