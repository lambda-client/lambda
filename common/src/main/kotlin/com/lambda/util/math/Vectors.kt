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

import com.lambda.util.math.MathUtils.sq
import net.minecraft.entity.Entity
import net.minecraft.util.math.*
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/* Direction */
val Direction.hitVecOffset: Vec3d get() = CENTER + vector * 0.5

fun EightWayDirection.rotateClockwise(steps: Int) =
    EightWayDirection.entries[(ordinal + steps) % 8]

/* Vec2f */
operator fun Vec2f.component1() = x
operator fun Vec2f.component2() = y

/* Vec3d */
fun Vec3d.approximate(other: Vec3d, precision: Double = 2.0E-4): Boolean =
    (subtract(other) distSq Vec3d.ZERO) > precision.pow(2)

val Vec3d.roundedBlockPos: BlockPos
    get() = BlockPos(x.roundToInt(), y.roundToInt(), z.roundToInt())

fun Vec3d.interpolate(value: Double, max: Vec3d) = lerp(value, this, max)

operator fun Vec3d.component1() = x
operator fun Vec3d.component2() = y
operator fun Vec3d.component3() = z

infix fun Vec3d.dist(other: Vec3d): Double = sqrt(this distSq other)
infix fun Vec3d.dist(other: Vec3i): Double = sqrt(this distSq other)
infix fun Vec3d.distSq(other: Vec3d): Double = squaredDistanceTo(other)
infix fun Vec3d.distSq(other: Vec3i): Double =
    squaredDistanceTo(other.x.toDouble(), other.y.toDouble(), other.z.toDouble())

infix operator fun Vec3d.plus(other: Vec3d): Vec3d = add(other)
infix operator fun Vec3d.plus(other: Vec3i): Vec3d = Vec3d(x + other.x, y + other.y, z + other.z)
infix operator fun Vec3d.plus(other: Double): Vec3d = add(other, other, other)
infix operator fun Vec3d.plus(other: Float): Vec3d = add(other.toDouble(), other.toDouble(), other.toDouble())
infix operator fun Vec3d.plus(other: Int): Vec3d = add(other.toDouble(), other.toDouble(), other.toDouble())
infix operator fun Vec3d.minus(other: Vec3d): Vec3d = subtract(other)
infix operator fun Vec3d.minus(other: Vec3i): Vec3d = Vec3d(x - other.x, y - other.y, z - other.z)
infix operator fun Vec3d.minus(other: Double): Vec3d = subtract(other, other, other)
infix operator fun Vec3d.minus(other: Float): Vec3d = subtract(other.toDouble(), other.toDouble(), other.toDouble())
infix operator fun Vec3d.minus(other: Int): Vec3d = subtract(other.toDouble(), other.toDouble(), other.toDouble())
infix operator fun Vec3d.times(other: Vec3d): Vec3d = multiply(other)
infix operator fun Vec3d.times(other: Vec3i): Vec3d = Vec3d(x * other.x, y * other.y, z * other.z)
infix operator fun Vec3d.times(other: Double): Vec3d = multiply(other)
infix operator fun Vec3d.times(other: Float): Vec3d = multiply(other.toDouble())
infix operator fun Vec3d.times(other: Int): Vec3d = multiply(other.toDouble())
infix operator fun Vec3d.div(other: Vec3d): Vec3d = multiply(1.0 / other.x, 1.0 / other.y, 1.0 / other.z)
infix operator fun Vec3d.div(other: Vec3i): Vec3d = Vec3d(x / other.x, y / other.y, z / other.z)
infix operator fun Vec3d.div(other: Double): Vec3d = times(1 / other)
infix operator fun Vec3d.div(other: Float): Vec3d = times(1 / other)
infix operator fun Vec3d.div(other: Int): Vec3d = times(1 / other)

/* Vec3i */
fun BlockPos.getHitVec(side: Direction): Vec3d =
    side.hitVecOffset + this

infix fun Vec3i.dist(other: Vec3d): Double = sqrt(this distSq other)
infix fun Vec3i.dist(other: Vec3i): Double = sqrt((this distSq other).toDouble())
infix fun Vec3i.distSq(other: Vec3d): Double = getSquaredDistance(other)
infix fun Vec3i.distSq(other: Vec3i): Int = (x - other.x).sq + (y - other.y).sq + (z - other.z).sq

infix operator fun Vec3i.plus(other: Vec3d): Vec3i = Vec3i(x + other.x.toInt(), y + other.y.toInt(), z + other.z.toInt())
infix operator fun Vec3i.plus(other: Vec3i): Vec3i = add(other)
infix operator fun Vec3i.plus(other: Double): Vec3i = add(other.toInt(), other.toInt(), other.toInt())
infix operator fun Vec3i.plus(other: Float): Vec3i = add(other.toInt(), other.toInt(), other.toInt())
infix operator fun Vec3i.plus(other: Int): Vec3i = add(other, other, other)
infix operator fun Vec3i.minus(other: Vec3d): Vec3i = Vec3i(x - other.x.toInt(), y - other.y.toInt(), z - other.z.toInt())
infix operator fun Vec3i.minus(other: Vec3i): Vec3i = subtract(other)
infix operator fun Vec3i.minus(other: Double): Vec3i = add(-other.toInt(), -other.toInt(), -other.toInt())
infix operator fun Vec3i.minus(other: Float): Vec3i = add(-other.toInt(), -other.toInt(), -other.toInt())
infix operator fun Vec3i.minus(other: Int): Vec3i = add(-other, -other, -other)
infix operator fun Vec3i.times(other: Vec3d): Vec3i = Vec3i(x * other.x.toInt(), y * other.y.toInt(), z * other.z.toInt())
infix operator fun Vec3i.times(other: Vec3i): Vec3i = Vec3i(x * other.x, y * other.y, z * other.z)
infix operator fun Vec3i.times(other: Double): Vec3i = multiply(other.toInt())
infix operator fun Vec3i.times(other: Float): Vec3i = multiply(other.toInt())
infix operator fun Vec3i.times(other: Int): Vec3i = multiply(other)
infix operator fun Vec3i.div(other: Vec3d): Vec3i = Vec3i((x / other.x).toInt(), (y / other.y).toInt(), (z / other.z).toInt())
infix operator fun Vec3i.div(other: Vec3i): Vec3i = Vec3i(x / other.x, y / other.y, z / other.z)
infix operator fun Vec3i.div(other: Double): Vec3i = times(1 / other)
infix operator fun Vec3i.div(other: Float): Vec3i = times(1 / other)
infix operator fun Vec3i.div(other: Int): Vec3i = times(1 / other)

/* Entity */
infix fun Entity.dist(other: Vec3d): Double = pos dist other
infix fun Entity.dist(other: Vec3i): Double = blockPos dist other
infix fun Entity.dist(other: Entity): Double = distanceTo(other).toDouble()
infix fun Entity.distSq(other: Vec3d): Double = pos distSq other
infix fun Entity.distSq(other: Vec3i): Int = blockPos distSq other
infix fun Entity.distSq(other: Entity): Double = squaredDistanceTo(other)

val UP = Vec3d(0.0, 1.0, 0.0)
val DOWN = Vec3d(0.0, -1.0, 0.0)
val CENTER = Vec3d(0.5, 0.5, 0.5)
