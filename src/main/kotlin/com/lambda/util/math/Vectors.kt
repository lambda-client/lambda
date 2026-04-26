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

import com.lambda.context.SafeContext
import com.lambda.util.BlockUtils.isLoaded
import com.lambda.util.math.MathUtils.floorToInt
import com.lambda.util.math.MathUtils.sq
import net.minecraft.entity.Entity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.EightWayDirection
import net.minecraft.util.math.Vec2f
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.Vec3i
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

fun BlockPos.getHitVec(side: Direction): Vec3d =
    side.hitVecOffset + this

/* Direction */
val Direction.hitVecOffset: Vec3d
    get() =
        CENTER + vector.vec3d * 0.5

fun EightWayDirection.rotateClockwise(steps: Int) =
    EightWayDirection.entries[(ordinal + steps) % 8]

/* Vec2f */
operator fun Vec2f.component1() = x
operator fun Vec2f.component2() = y

infix fun Vec2f.dist(other: Vec2f): Float = sqrt(this distSq other)
infix fun Vec2f.dist(other: Vec2d): Float = sqrt(this distSq other)
infix fun Vec2f.distSq(other: Vec2f): Float = distanceSquared(other)
infix fun Vec2f.distSq(other: Vec2d): Float =
    sqrt((other.x - x).sq * (other.y - y).sq).toFloat()

infix operator fun Vec2f.plus(other: Vec2f): Vec2f = add(other)
infix operator fun Vec2f.plus(other: Vec2d): Vec2f = Vec2f((x + other.x).toFloat(), (y + other.y).toFloat())
infix operator fun Vec2f.plus(other: Double): Vec2f = add(other.toFloat())
infix operator fun Vec2f.plus(other: Float): Vec2f = add(other)
infix operator fun Vec2f.plus(other: Int): Vec2f = add(other.toFloat())

infix operator fun Vec2f.minus(other: Vec2f): Vec2f = add(-other)

infix operator fun Vec2f.minus(other: Vec2d): Vec2f = Vec2f((x - other.x).toFloat(), (y - other.y).toFloat())
infix operator fun Vec2f.minus(other: Double): Vec2f = add(-other.toFloat())
infix operator fun Vec2f.minus(other: Float): Vec2f = add(-other)
infix operator fun Vec2f.minus(other: Int): Vec2f = add(-other.toFloat())

infix operator fun Vec2f.times(other: Vec2f): Vec2f = Vec2f(x * other.x, y * other.y)
infix operator fun Vec2f.times(other: Vec2d): Vec2f = Vec2f((x * other.x).toFloat(), (y * other.y).toFloat())
infix operator fun Vec2f.times(other: Double): Vec2f = Vec2f(x * other.toFloat(), y * other.toFloat())
infix operator fun Vec2f.times(other: Float): Vec2f = Vec2f(x * other, y * other)
infix operator fun Vec2f.times(other: Int): Vec2f = Vec2f(x * other.toFloat(), y * other.toFloat())

infix operator fun Vec2f.div(other: Vec2f): Vec2f = Vec2f(x / other.x, x / other.y)
infix operator fun Vec2f.div(other: Vec2d): Vec2d = Vec2d(x / other.x, y / other.y)
infix operator fun Vec2f.div(other: Double): Vec2f = times(1.0 / other)
infix operator fun Vec2f.div(other: Float): Vec2f = times(1.0 / other)
infix operator fun Vec2f.div(other: Int): Vec2f = times(1.0 / other)

operator fun Vec2f.unaryMinus(): Vec2f = negate()

/* Chunk */
val ChunkPos.center: ChunkPos
    get() = ChunkPos(centerX, centerZ)

infix fun ChunkPos.dist(other: Vec3i): Double = other dist this
infix fun ChunkPos.dist(other: Vec3d): Double = other dist this
infix fun ChunkPos.dist(other: ChunkPos): Double = sqrt((this distSq other).toDouble())
infix fun ChunkPos.distCenter(other: Vec3i): Double = sqrt((this distSqCenter other).toDouble())
infix fun ChunkPos.distCenter(other: Vec3d): Double = sqrt(this distSqCenter other)
infix fun ChunkPos.distCenter(other: ChunkPos): Double = sqrt((this distSqCenter other).toDouble())
infix fun ChunkPos.distSq(other: Vec3i): Int = other distSq this
infix fun ChunkPos.distSq(other: Vec3d): Double = other distSq this
infix fun ChunkPos.distSq(other: ChunkPos): Int = (other.x - x).sq + (other.z - z).sq
infix fun ChunkPos.distSqCenter(other: Vec3i): Int = (other.x - centerX).sq + (other.z - centerZ).sq
infix fun ChunkPos.distSqCenter(other: Vec3d): Double = (other.x - centerX).sq + (other.z - centerZ).sq
infix fun ChunkPos.distSqCenter(other: ChunkPos): Int = (other.centerX - centerX).sq + (other.centerZ - centerZ).sq

/* Vec3d */
fun Vec3d.approximate(other: Vec3d, precision: Double = 2.0E-4): Boolean =
    (subtract(other) distSq Vec3d.ZERO) > precision.pow(2)

val Vec3d.roundedBlockPos: BlockPos
    get() = BlockPos(x.roundToInt(), y.roundToInt(), z.roundToInt())

val Vec3d.flooredBlockPos: BlockPos
    get() = BlockPos(x.floorToInt(), y.floorToInt(), z.floorToInt())

val Entity.netherCoord: Vec3d get() = pos.multiply(0.125, 1.0, 0.125)
val Entity.overworldCoord: Vec3d get() = pos.multiply(8.0, 1.0, 8.0)

fun Vec3d.interpolate(value: Double, max: Vec3d) = lerp(value, this, max)
fun Vec3d.interpolate(value: Float, max: Vec3d) = lerp(value.toDouble(), this, max)

operator fun Vec3d.component1() = x
operator fun Vec3d.component2() = y
operator fun Vec3d.component3() = z

infix fun Vec3d.dist(other: Vec3d): Double = sqrt(this distSq other)
infix fun Vec3d.dist(other: Vec3i): Double = sqrt(this distSq other)
infix fun Vec3d.dist(other: ChunkPos): Double = sqrt(this distSq other)
infix fun Vec3d.distSq(other: Vec3d): Double = (other.x - x).sq + (other.y - y).sq + (other.z - z).sq
infix fun Vec3d.distSq(other: Vec3i): Double = (other.x - x).sq + (other.y - y).sq + (other.z - z).sq
infix fun Vec3d.distSq(other: ChunkPos): Double = (other.x * 16 - x).sq + (other.z * 16 - z).sq

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
infix operator fun Vec3d.div(other: Double): Vec3d = times(1.0 / other)
infix operator fun Vec3d.div(other: Float): Vec3d = times(1.0 / other)
infix operator fun Vec3d.div(other: Int): Vec3d = times(1.0 / other)

infix operator fun ClosedRange<Double>.rangeTo(other: Double) = Vec3d(start, endInclusive, other)
infix operator fun ClosedRange<Float>.rangeTo(other: Float) =
    Vec3d(start.toDouble(), endInclusive.toDouble(), other.toDouble())

infix operator fun ClosedRange<Int>.rangeTo(other: Int) =
    Vec3d(start.toDouble(), endInclusive.toDouble(), other.toDouble())

infix operator fun OpenEndRange<Double>.rangeTo(other: Double) = Vec3d(start, endExclusive, other)
infix operator fun OpenEndRange<Float>.rangeTo(other: Float) =
    Vec3d(start.toDouble(), endExclusive.toDouble(), other.toDouble())

infix operator fun OpenEndRange<Int>.rangeTo(other: Int) = BlockPos.Mutable(start, endExclusive, other)

/* Vec3i */
val Vec3i.vec3d get() = Vec3d(x.toDouble(), y.toDouble(), z.toDouble())

infix fun Vec3i.dist(other: Vec3d): Double = sqrt(this distSq other)
infix fun Vec3i.dist(other: Vec3i): Double = sqrt((this distSq other).toDouble())
infix fun Vec3i.dist(other: ChunkPos): Double = sqrt((this distSq other).toDouble())
infix fun Vec3i.distSq(other: Vec3d): Double = getSquaredDistance(other)
infix fun Vec3i.distSq(other: Vec3i): Int = (x - other.x).sq + (y - other.y).sq + (z - other.z).sq
infix fun Vec3i.distSq(other: ChunkPos): Int = (other.x * 16 - x).sq + (other.z * 16 - z).sq

infix operator fun Vec3i.plus(other: Vec3i): Vec3i = add(other)
infix operator fun Vec3i.plus(other: Int): Vec3i = add(other, other, other)

infix operator fun Vec3i.minus(other: Vec3i): Vec3i = subtract(other)
infix operator fun Vec3i.minus(other: Int): Vec3i = add(-other, -other, -other)

infix operator fun Vec3i.times(other: Vec3i): Vec3i = Vec3i(x * other.x, y * other.y, z * other.z)
infix operator fun Vec3i.times(other: Int): Vec3i = multiply(other)

infix operator fun Vec3i.div(other: Vec3i): Vec3i = Vec3i(x / other.x, y / other.y, z / other.z)
infix operator fun Vec3i.div(other: Int): Vec3i = times(1 / other)

infix fun Vec2d.dist(other: Vec2d): Double = sqrt(this distSq other)
infix fun Vec2d.dist(other: Vec2f): Double = sqrt(this distSq other)
infix fun Vec2d.distSq(other: Vec2d): Double = (other.x - x).pow(2) + (other.y - y).pow(2)
infix fun Vec2d.distSq(other: Vec2f): Double = (other.x - x).pow(2) + (other.y - y).pow(2)

fun Vec2d.normal(): Vec2d {
    val length = sqrt(x * x + y * y)
    return if (length != 0.0) Vec2d(x / length, y / length) else Vec2d.ZERO
}

/* Entity */
infix fun Entity.dist(other: Vec3d): Double = pos dist other
infix fun Entity.dist(other: Vec3i): Double = blockPos dist other
infix fun Entity.dist(other: Entity): Double = distanceTo(other).toDouble()
infix fun Entity.distSq(other: Vec3d): Double = pos distSq other
infix fun Entity.distSq(other: Vec3i): Int = blockPos distSq other
infix fun Entity.distSq(other: Entity): Double = squaredDistanceTo(other)

context(safeContext: SafeContext)
val Vec3d.isLoaded get() = flooredBlockPos.isLoaded

val UP = Vec3d(0.0, 1.0, 0.0)
val DOWN = Vec3d(0.0, -1.0, 0.0)
val CENTER = Vec3d(0.5, 0.5, 0.5)
