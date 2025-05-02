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

package com.lambda.util.world

import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.Vec3i
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Represents a position in the world encoded as a long.
 *
 * [ X (26 bits) | Z (26 bits) | Y (12 bits) ]
 *
 * The position is encoded as a 64-bit long where the X and Z coordinates are stored in the 26 most significant bits,
 * and the Y coordinate is stored in the 12 least significant bits.
 * This encoding allows for a maximum world size of ±33,554,432 blocks
 * in the X and Z directions and ±2,048 blocks in the Y direction, which is more than needed.
 */
typealias FastVector = Long

internal const val X_BITS = 26
internal const val Z_BITS = 26
internal const val Y_BITS = 12

internal const val X_SHIFT = Y_BITS + Z_BITS
internal const val Z_SHIFT = Y_BITS

internal const val X_MASK = (1L shl X_BITS) - 1L
internal const val Z_MASK = (1L shl Z_BITS) - 1L
internal const val Y_MASK = (1L shl Y_BITS) - 1L

internal const val MIN_X = -(1L shl X_BITS - 1)
internal const val MIN_Z = -(1L shl Z_BITS - 1)
internal const val MAX_X = (1L shl X_BITS - 1) - 1L
internal const val MAX_Z = (1L shl Z_BITS - 1) - 1L

/**
 * Creates a new position from the given coordinates.
 */
fun fastVectorOf(x: Long, y: Long, z: Long): FastVector {
    require(x in MIN_X..MAX_X) { "X coordinate out of bounds for $X_BITS bits: $x" }
    require(z in MIN_Z..MAX_Z) { "Z coordinate out of bounds for $Z_BITS bits: $z" }

    return ((x and X_MASK) shl X_SHIFT) or ((z and Z_MASK) shl Z_SHIFT) or (y and Y_MASK)
}

/**
 * Creates a new position from the given coordinates.
 */
fun fastVectorOf(x: Int, y: Int, z: Int): FastVector = fastVectorOf(x.toLong(), y.toLong(), z.toLong())

/**
 * Gets the X coordinate from the position.
 */
val FastVector.x: Int
    get() = ((this shr X_SHIFT and X_MASK).toInt() shl (32 - X_BITS)) shr (32 - X_BITS)

/**
 * Gets the Z coordinate from the position.
 */
val FastVector.z: Int
    get() = ((this shr Z_SHIFT and Z_MASK).toInt() shl (32 - Z_BITS)) shr (32 - Z_BITS)

/**
 * Gets the Y coordinate from the position.
 */
val FastVector.y: Int
    get() = ((this and Y_MASK).toInt() shl (32 - Y_BITS)) shr (32 - Y_BITS)

/**
 * Sets the X coordinate of the position.
 */
infix fun FastVector.setX(x: Int): FastVector = bitSetTo(x.toLong(), X_SHIFT, X_BITS)

/**
 * Sets the Y coordinate of the position.
 */
infix fun FastVector.setY(y: Int): FastVector = bitSetTo(y.toLong(), 0, Y_BITS)

/**
 * Sets the Z coordinate of the position.
 */
infix fun FastVector.setZ(z: Int): FastVector = bitSetTo(z.toLong(), Z_SHIFT, Z_BITS)

/**
 * Adds the given value to the X coordinate.
 */
infix fun FastVector.addX(value: Int): FastVector = setX(x + value)

/**
 * Adds the given value to the Y coordinate.
 */
infix fun FastVector.addY(value: Int): FastVector = setY(y + value)

/**
 * Adds the given value to the Z coordinate.
 */
infix fun FastVector.addZ(value: Int): FastVector = setZ(z + value)

fun FastVector.offset(x: Int, y: Int, z: Int): FastVector = fastVectorOf(this.x + x, this.y + y, this.z + z)

fun FastVector.manhattanLength() = abs(x) + abs(y) + abs(z)

fun FastVector.length() = sqrt((abs(x * x) + abs(y * y) + abs(z * z)).toDouble())

/**
 * Adds the given vector to the position.
 */
infix fun FastVector.add(vec: FastVector): FastVector = fastVectorOf(x + vec.x, y + vec.y, z + vec.z)

/**
 * Adds the given vector to the position.
 */
operator fun FastVector.plus(vec: Vec3i): FastVector = fastVectorOf(x + vec.x, y + vec.y, z + vec.z)

/**
 * Adds the given vector to the position.
 */
operator fun FastVector.plus(vec: Vec3d): FastVector =
    fastVectorOf(x + vec.x.toLong(), y + vec.y.toLong(), z + vec.z.toLong())

/**
 * Subtracts the given vector from the position.
 */
infix fun FastVector.minus(vec: FastVector): FastVector = fastVectorOf(x - vec.x, y - vec.y, z - vec.z)

/**
 * Subtracts the given vector from the position.
 */
operator fun FastVector.minus(vec: Vec3i): FastVector = fastVectorOf(x - vec.x, y - vec.y, z - vec.z)

/**
 * Subtracts the given vector from the position.
 */
operator fun FastVector.minus(vec: Vec3d): FastVector =
    fastVectorOf(x - vec.x.toLong(), y - vec.y.toLong(), z - vec.z.toLong())

/**
 * Multiplies the position by the given scalar.
 */
infix fun FastVector.times(scalar: Int): FastVector = fastVectorOf(x * scalar, y * scalar, z * scalar)

/**
 * Multiplies the position by the given scalar.
 */
infix fun FastVector.times(scalar: Double): FastVector =
    fastVectorOf((x * scalar).toLong(), (y * scalar).toLong(), (z * scalar).toLong())

/**
 * Divides the position by the given scalar.
 */
infix fun FastVector.div(scalar: Int): FastVector = fastVectorOf(x / scalar, y / scalar, z / scalar)

/**
 * Divides the position by the given scalar.
 */
infix fun FastVector.div(scalar: Double): FastVector =
    fastVectorOf((x / scalar).toLong(), (y / scalar).toLong(), (z / scalar).toLong())

/**
 * Modulo the position by the given scalar.
 */
infix fun FastVector.remainder(scalar: Int): FastVector = fastVectorOf(x % scalar, y % scalar, z % scalar)

/**
 * Modulo the position by the given scalar.
 */
infix fun FastVector.remainder(scalar: Double): FastVector =
    fastVectorOf((x % scalar).toLong(), (y % scalar).toLong(), (z % scalar).toLong())

infix fun FastVector.dist(other: FastVector): Double = sqrt(distSq(other))

/**
 * Returns the squared distance between this position and the other.
 */
infix fun FastVector.distSq(other: FastVector): Double {
    val dx = x - other.x
    val dy = y - other.y
    val dz = z - other.z
    return (dx * dx + dy * dy + dz * dz).toDouble()
}

/**
 * Returns the Manhattan distance between this position and the other.
 */
infix fun FastVector.distManhattan(other: FastVector): Double {
    val dx = x - other.x
    val dy = y - other.y
    val dz = z - other.z
    return (abs(dx) + abs(dy) + abs(dz)).toDouble()
}

/**
 * Returns the squared distance between this position and the Vec3i.
 */
infix fun FastVector.distSq(other: Vec3i): Double {
    val dx = x - other.x
    val dy = y - other.y
    val dz = z - other.z
    return (dx * dx + dy * dy + dz * dz).toDouble()
}

/**
 * Returns the squared distance between this position and the Vec3d.
 */
infix fun FastVector.distSq(other: Vec3d): Double {
    val dx = x - other.x.toLong()
    val dy = y - other.y.toLong()
    val dz = z - other.z.toLong()
    return (dx * dx + dy * dy + dz * dz).toDouble()
}

/**
 * Adds a [net.minecraft.util.math.Direction] offset to the fast vector
 */
fun FastVector.offset(dir: Direction) =
    fastVectorOf(x + dir.offsetX, y + dir.offsetY, z + dir.offsetZ)

/**
 * Converts a [Vec3i] to a [FastVector].
 */
fun Vec3i.toFastVec(): FastVector = fastVectorOf(x.toLong(), y.toLong(), z.toLong())

/**
 * Converts a [Vec3d] to a [FastVector].
 */
fun Vec3d.toFastVec(): FastVector = fastVectorOf(x.toLong(), y.toLong(), z.toLong())

/**
 * Converts the [FastVector] into a [Vec3d].
 */
fun FastVector.toVec3d(): Vec3d = Vec3d(x.toDouble(), y.toDouble(), z.toDouble())

/**
 * [FastVector] to a centered [Vec3d]
 */
fun FastVector.toCenterVec3d(): Vec3d = Vec3d(x + 0.5, y + 0.5, z + 0.5)

/**
 * Converts the [FastVector] into a [BlockPos].
 */
fun FastVector.toBlockPos(): BlockPos = BlockPos(x, y, z)

/**
 * Sets n bits to a value at a given position.
 */
internal fun Long.bitSetTo(value: Long, position: Int, length: Int): Long {
    val mask = (1L shl length) - 1L
    return this and (mask shl position).inv() or (value and mask shl position)
}

val FastVector.string: String
    get() = "($x, $y, $z)"
