package com.lambda.util.world

import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.Vec3i

/**
 * Represents a position in the world encoded as a long.
 *
 * [ X (26 bits) | Y (12 bits) | Z (26 bits) ]
 *
 * The position is encoded as a 64-bit long where the X and Z coordinates are stored in the most significant 26 bits
 * and the Y coordinate is stored in the middle 12 bits. This encoding allows for a maximum world size of
 * ±33,554,432 blocks in the X and Z directions and ±2,048 blocks in the Y direction, which is more than
 * needed.
 */
typealias FastVector = Long

const val X_BITS = 26
const val Y_BITS = 12
const val Z_BITS = 26

const val X_SHIFT = Y_BITS + Z_BITS
const val Y_SHIFT = Z_BITS

const val X_MASK = (1L shl X_BITS) - 1L
const val Y_MASK = (1L shl Y_BITS) - 1L
const val Z_MASK = (1L shl Z_BITS) - 1L

/**
 * Gets the X coordinate from the position.
 */
val FastVector.x: Long
    get() = this shr X_SHIFT

/**
 * Gets the X coordinate from the position.
 */
val FastVector.xInt: Int
    get() = x.toInt()

/**
 * Gets the Y coordinate from the position.
 */
val FastVector.y: Long
    get() = this shr Y_SHIFT and Y_MASK

/**
 * Gets the Y coordinate from the position.
 */
val FastVector.yInt: Int
    get() = y.toInt()

/**
 * Gets the Z coordinate from the position.
 */
val FastVector.z: Long
    get() = this shl (64 - Z_BITS) shr (64 - Z_BITS)

/**
 * Gets the Z coordinate from the position.
 */
val FastVector.zInt
    get() = z.toInt()

/**
 * Adds the given value to the X coordinate.
 */
fun FastVector.addX(value: Long): FastVector = fastVectorOf(x + value, y, z)

/**
 * Adds the given value to the Y coordinate.
 */
fun FastVector.addY(value: Long): FastVector = fastVectorOf(x, y + value, z)

/**
 * Adds the given value to the Z coordinate.
 */
fun FastVector.addZ(value: Long): FastVector = fastVectorOf(x, y, z + value)

/**
 * Adds the given vector to the position.
 * @return The new position.
 */
fun FastVector.add(vec: FastVector): FastVector = fastVectorOf(x + vec.x, y + vec.y, z + vec.z)

/**
 * Adds the given vector to the position.
 * @return The new position.
 */
fun FastVector.add(vec: Vec3i): FastVector = fastVectorOf(x + vec.x, y + vec.y, z + vec.z)

/**
 * Creates a new position from the given coordinates.
 */
fun fastVectorOf(x: Long, y: Long, z: Long): FastVector =
    ((x and X_MASK) shl X_SHIFT) or ((y and Y_MASK) shl Y_SHIFT) or (z and Z_MASK)

/**
 * Creates a new position from the given coordinates.
 */
fun fastVectorOf(x: Int, y: Int, z: Int): FastVector = fastVectorOf(x.toLong(), y.toLong(), z.toLong())

/**
 * Encodes the vector as a position.
 * @return The encoded position.
 */
fun Vec3i.encoded(): FastVector = fastVectorOf(x.toLong(), y.toLong(), z.toLong())

/**
 * Encodes the vector as a position.
 * @return The encoded position.
 */
fun Vec3d.encoded(): FastVector = fastVectorOf(x.toLong(), y.toLong(), z.toLong())

/**
 * Decodes the position into a vector.
 */
fun FastVector.decoded(): Vec3d = Vec3d(x.toDouble(), y.toDouble(), z.toDouble())

/**
 * Decodes the position into a vector.
 */
fun FastVector.decodedInt(): Vec3i = Vec3i(x.toInt(), y.toInt(), z.toInt())
