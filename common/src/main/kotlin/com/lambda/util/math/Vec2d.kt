package com.lambda.util.math

import net.minecraft.util.math.Vec2f
import kotlin.math.roundToInt

data class Vec2d(val x: Double, val y: Double) {
    constructor(x: Int, y: Int) : this(x.toDouble(), y.toDouble())
    constructor(x: Float, y: Float) : this(x.toDouble(), y.toDouble())
    constructor(x: Short, y: Short) : this(x.toDouble(), y.toDouble())

    operator fun plus(vec2d: Vec2d) = plus(vec2d.x, vec2d.y)

    operator fun plus(add: Double) = plus(add, add)

    fun plus(x: Double, y: Double) = Vec2d(this.x + x, this.y + y)

    operator fun minus(vec2d: Vec2d) = minus(vec2d.x, vec2d.y)

    operator fun minus(sub: Double) = minus(sub, sub)

    fun minus(x: Double, y: Double) = plus(-x, -y)

    operator fun times(vec2d: Vec2d) = times(vec2d.x, vec2d.y)

    operator fun times(multiplier: Double) = times(multiplier, multiplier)

    fun times(x: Double, y: Double) = Vec2d(this.x * x, this.y * y)

    operator fun div(vec2d: Vec2d) = div(vec2d.x, vec2d.y)

    operator fun div(divider: Double) = div(divider, divider)

    fun div(x: Double, y: Double) = Vec2d(this.x / x, this.y / y)

    val vec2f = Vec2f(x.toFloat(), y.toFloat())
    val rounded get() = Vec2d(x.roundToInt(), y.roundToInt())

    companion object {
        val ZERO: Vec2d = Vec2d(0.0, 0.0)
        val ONE: Vec2d = Vec2d(1.0, 1.0)

        val RIGHT: Vec2d = Vec2d(1.0, 0.0)
        val LEFT: Vec2d = Vec2d(-1.0, 0.0)
        val TOP: Vec2d = Vec2d(0.0, 1.0)
        val BOTTOM: Vec2d = Vec2d(0.0, -1.0)
    }
}