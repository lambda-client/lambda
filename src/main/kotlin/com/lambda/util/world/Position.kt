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

package com.lambda.util.world

import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.Vec3i

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

internal const val XBits = 26
internal const val ZBits = 26
internal const val YBits = 12

internal const val XShift = YBits + ZBits
internal const val ZShift = YBits

internal const val XMask = (1L shl XBits) - 1L
internal const val ZMask = (1L shl ZBits) - 1L
internal const val YMask = (1L shl YBits) - 1L

internal const val MinX = -(1L shl XBits - 1)
internal const val MinZ = -(1L shl ZBits - 1)
internal const val MaxX = (1L shl XBits - 1) - 1L
internal const val MaxZ = (1L shl ZBits - 1) - 1L

/**
 * Serialized representation of (1, 1, 1)
 */
const val FOne = 274945015809L

fun fastVectorOf(x: Long, y: Long, z: Long): FastVector {
    require(x in MinX..MaxX) { "X coordinate out of bounds for $XBits bits: $x" }
    require(z in MinZ..MaxZ) { "Z coordinate out of bounds for $ZBits bits: $z" }

    return ((x and XMask) shl XShift) or ((z and ZMask) shl ZShift) or (y and YMask)
}

fun fastVectorOf(x: Int, y: Int, z: Int): FastVector = fastVectorOf(x.toLong(), y.toLong(), z.toLong())

val FastVector.x: Int
    get() = ((this shr XShift and XMask).toInt() shl (32 - XBits)) shr (32 - XBits)

val FastVector.z: Int
    get() = ((this shr ZShift and ZMask).toInt() shl (32 - ZBits)) shr (32 - ZBits)

val FastVector.y: Int
    get() = ((this and YMask).toInt() shl (32 - YBits)) shr (32 - YBits)

infix fun FastVector.setX(x: Int): FastVector = bitSetTo(x.toLong(), XShift, XBits)
infix fun FastVector.setY(y: Int): FastVector = bitSetTo(y.toLong(), 0, YBits)
infix fun FastVector.setZ(z: Int): FastVector = bitSetTo(z.toLong(), ZShift, ZBits)

infix fun FastVector.addX(value: Int): FastVector = setX(x + value)
infix fun FastVector.addY(value: Int): FastVector = setY(y + value)
infix fun FastVector.addZ(value: Int): FastVector = setZ(z + value)

infix fun FastVector.plus(vec: FastVector): FastVector = fastVectorOf(x + vec.x, y + vec.y, z + vec.z)
infix fun FastVector.plus(vec: Vec3i): FastVector = fastVectorOf(x + vec.x, y + vec.y, z + vec.z)
infix fun FastVector.plus(vec: Vec3d): FastVector =
    fastVectorOf(x + vec.x.toLong(), y + vec.y.toLong(), z + vec.z.toLong())

infix fun FastVector.minus(vec: FastVector): FastVector = fastVectorOf(x - vec.x, y - vec.y, z - vec.z)
infix fun FastVector.minus(vec: Vec3i): FastVector = fastVectorOf(x - vec.x, y - vec.y, z - vec.z)
infix fun FastVector.minus(vec: Vec3d): FastVector =
    fastVectorOf(x - vec.x.toLong(), y - vec.y.toLong(), z - vec.z.toLong())

infix fun FastVector.times(scalar: Int): FastVector = fastVectorOf(x * scalar, y * scalar, z * scalar)
infix fun FastVector.times(scalar: Double): FastVector =
    fastVectorOf((x * scalar).toLong(), (y * scalar).toLong(), (z * scalar).toLong())

infix fun FastVector.div(scalar: Int): FastVector = fastVectorOf(x / scalar, y / scalar, z / scalar)
infix fun FastVector.div(scalar: Double): FastVector =
    fastVectorOf((x / scalar).toLong(), (y / scalar).toLong(), (z / scalar).toLong())

infix fun FastVector.remainder(scalar: Int): FastVector = fastVectorOf(x % scalar, y % scalar, z % scalar)
infix fun FastVector.remainder(scalar: Double): FastVector =
    fastVectorOf((x % scalar).toLong(), (y % scalar).toLong(), (z % scalar).toLong())

infix fun FastVector.distSq(other: FastVector): Double {
    val dx = x - other.x
    val dy = y - other.y
    val dz = z - other.z
    return (dx * dx + dy * dy + dz * dz).toDouble()
}

infix fun FastVector.distSq(other: Vec3i): Double {
    val dx = x - other.x
    val dy = y - other.y
    val dz = z - other.z
    return (dx * dx + dy * dy + dz * dz).toDouble()
}

infix fun FastVector.distSq(other: Vec3d): Double {
    val dx = x - other.x.toLong()
    val dy = y - other.y.toLong()
    val dz = z - other.z.toLong()
    return (dx * dx + dy * dy + dz * dz).toDouble()
}

fun FastVector.offset(dir: Direction) =
    fastVectorOf(x + dir.offsetX, y + dir.offsetY, z + dir.offsetZ)

fun Vec3i.toFastVec(): FastVector = fastVectorOf(x.toLong(), y.toLong(), z.toLong())
fun Vec3d.toFastVec(): FastVector = fastVectorOf(x.toLong(), y.toLong(), z.toLong())

fun FastVector.toVec3d(): Vec3d = Vec3d(x.toDouble(), y.toDouble(), z.toDouble())
fun FastVector.toBlockPos(): BlockPos = BlockPos(x, y, z)

/**
 * Sets n bits to a value at a given position.
 */
internal fun Long.bitSetTo(value: Long, position: Int, length: Int): Long {
    val mask = (1L shl length) - 1L
    return this and (mask shl position).inv() or (value and mask shl position)
}
