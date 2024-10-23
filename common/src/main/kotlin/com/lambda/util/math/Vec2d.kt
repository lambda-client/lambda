/*
 * Copyright 2024 Lambda
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

import kotlin.math.roundToInt

data class Vec2d(val x: Double, val y: Double) {
    constructor(x: Int, y: Int) : this(x.toDouble(), y.toDouble())
    constructor(x: Float, y: Float) : this(x.toDouble(), y.toDouble())

    operator fun unaryPlus() = this
    operator fun plus(vec2d: Vec2d) = plus(vec2d.x, vec2d.y)
    operator fun plus(add: Double) = plus(add, add)
    fun plus(x: Double, y: Double) = Vec2d(this.x + x, this.y + y)

    operator fun unaryMinus() = Vec2d(-x, -y)
    operator fun minus(vec2d: Vec2d) = minus(vec2d.x, vec2d.y)
    operator fun minus(sub: Double) = minus(sub, sub)
    fun minus(x: Double, y: Double) = plus(-x, -y)

    operator fun times(vec2d: Vec2d) = times(vec2d.x, vec2d.y)
    operator fun times(multiplier: Double) = times(multiplier, multiplier)
    fun times(x: Double, y: Double) = Vec2d(this.x * x, this.y * y)

    operator fun div(vec2d: Vec2d) = div(vec2d.x, vec2d.y)
    operator fun div(divider: Double) = div(divider, divider)
    fun div(x: Double, y: Double) = Vec2d(this.x / x, this.y / y)

    fun roundToInt(): Vec2d = Vec2d(this.x.roundToInt(), this.y.roundToInt())

    companion object {
        val ZERO = Vec2d(0.0, 0.0)
        val ONE = Vec2d(1.0, 1.0)

        val LEFT = Vec2d(-1.0, 0.0)
        val RIGHT = Vec2d(1.0, 0.0)
        val TOP = Vec2d(0.0, -1.0)
        val BOTTOM = Vec2d(0.0, 1.0)
    }
}
