/*
 * Copyright 2026 Lambda
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

import kotlin.math.max
import kotlin.math.min

data class Rect(private val pos1: Vec2d, private val pos2: Vec2d) {
    val left get() = pos1.x
    val top get() = pos1.y
    val right get() = pos2.x
    val bottom get() = pos2.y

    val leftTop get() = Vec2d(left, top)
    val rightTop get() = Vec2d(right, top)
    val rightBottom get() = Vec2d(right, bottom)
    val leftBottom get() = Vec2d(left, bottom)

    val size get() = Vec2d(right - left, bottom - top)
    val center get() = lerp(0.5, pos1, pos2)

    infix operator fun plus(vec2d: Vec2d) = Rect(pos1 + vec2d, pos2 + vec2d)
    infix operator fun minus(vec2d: Vec2d) = Rect(pos1 - vec2d, pos2 - vec2d)

    fun moveFirst(vec2d: Vec2d) = Rect(pos1 + vec2d, pos2)
    fun moveSecond(vec2d: Vec2d) = Rect(pos1, pos2 + vec2d)

    fun expand(amount: Double) = Rect(pos1 - amount, pos2 + amount)
    fun shrink(amount: Double) = expand(-amount)

    fun clamp(rect: Rect) =
        Rect(
            Vec2d(max(left, rect.left), max(top, rect.top)),
            Vec2d(min(right, rect.right), min(bottom, rect.bottom))
        )

    operator fun contains(point: Vec2d): Boolean {
        if (size.x <= 0.0 || size.y <= 0.0) return false
        return point.x in left..right && point.y in top..bottom
    }

    operator fun contains(other: Rect): Boolean {
        if (size.x <= 0.0 || size.y <= 0.0) return false
        return other.leftTop in this || other.rightBottom in this || leftTop in other || rightBottom in other
    }

    companion object {
        val Zero = Rect(Vec2d.Zero, Vec2d.Zero)

        fun basedOn(base: Vec2d, width: Double, height: Double) =
            Rect(base, base + Vec2d(width, height))

        fun basedOn(base: Vec2d, width: Int, height: Int) =
            Rect(base, base + Vec2d(width, height))

        fun basedOn(base: Vec2d, size: Vec2d) =
            Rect(base, base + size)

        fun Rect.inv() = Rect(rightBottom, leftTop)
    }
}
