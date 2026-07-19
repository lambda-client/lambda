
package com.minato.util.math

import kotlin.math.roundToInt

data class Vec2d(val x: Double, val y: Double) {
    constructor(x: Float, y: Float) : this(x.toDouble(), y.toDouble())
    constructor(x: Int, y: Int) : this(x.toDouble(), y.toDouble())

    operator fun unaryMinus() = Vec2d(-x, -y)

    fun plus(x: Double, y: Double) = Vec2d(this.x + x, this.y + y)
    infix operator fun plus(other: Vec2d) = plus(other.x, other.y)
    infix operator fun plus(other: Double) = plus(other, other)
    infix operator fun plus(other: Float): Vec2d = plus(other.toDouble(), other.toDouble())
    infix operator fun plus(other: Int): Vec2d = plus(other.toDouble(), other.toDouble())

    fun minus(x: Double, y: Double) = Vec2d(this.x - x, this.y - y)
    infix operator fun minus(other: Vec2d): Vec2d = minus(other.x, other.y)
    infix operator fun minus(other: Double): Vec2d = minus(other, other)
    infix operator fun minus(other: Float): Vec2d = minus(other.toDouble(), other.toDouble())
    infix operator fun minus(other: Int): Vec2d = minus(other.toDouble(), other.toDouble())

    fun times(x: Double, y: Double) = Vec2d(this.x * x, this.y * y)
    infix operator fun times(other: Double) = times(other, other)
    infix operator fun times(other: Vec2d) = times(other.x, other.y)
    infix operator fun times(other: Float): Vec2d = times(other.toDouble(), other.toDouble())
    infix operator fun times(other: Int): Vec2d = times(other.toDouble(), other.toDouble())

    fun div(x: Double, y: Double) = Vec2d(this.x / x, this.y / y)
    infix operator fun div(other: Vec2d) = div(other.x, other.y)
    infix operator fun div(other: Double) = div(other, other)
    infix operator fun div(other: Float): Vec2d = div(other.toDouble(), other.toDouble())
    infix operator fun div(other: Int): Vec2d = div(other.toDouble(), other.toDouble())

    fun roundToInt() = Vec2d(x.roundToInt(), y.roundToInt())

    companion object {
        val ZERO = Vec2d(0.0, 0.0)
        val ONE = Vec2d(1.0, 1.0)

        val LEFT = Vec2d(-1.0, 0.0)
        val RIGHT = Vec2d(1.0, 0.0)
        val TOP = Vec2d(0.0, -1.0)
        val BOTTOM = Vec2d(0.0, 1.0)
    }
}
