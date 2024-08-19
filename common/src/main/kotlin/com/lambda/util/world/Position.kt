package com.lambda.util.world

import net.minecraft.util.math.BlockPos
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
internal const val MIN_Y = -(1L shl Y_BITS - 1)
internal const val MAX_X = (1L shl X_BITS - 1) - 1L
internal const val MAX_Z = (1L shl Z_BITS - 1) - 1L
internal const val MAX_Y = (1L shl Y_BITS - 1) - 1L

internal fun Long.bitSetTo(value: Long, position: Int, length: Int): Long {
    val mask = (1L shl length) - 1L
    return this and (mask shl position).inv() or (value and mask shl position)
}

/**
 * Creates a new position from the given coordinates.
 */
fun fastVectorOf(x: Long, y: Long, z: Long): FastVector {
    require(x in MIN_X..MAX_X) { "X coordinate out of bounds for $X_BITS bits: $x" }
    require(y in MIN_Y..MAX_Y) { "Y coordinate out of bounds for $Y_BITS bits: $y" }
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
    get() {
        val x = (this shr X_SHIFT and X_MASK).toInt()
        return if (x and (1 shl X_BITS - 1) != 0) x - (1 shl X_BITS) else x
    }

/**
 * Gets the Z coordinate from the position.
 */
val FastVector.z: Int
    get() {
        val z = (this shr Z_SHIFT and Z_MASK).toInt()
        return if (z and (1 shl Z_BITS - 1) != 0) z - (1 shl Z_BITS) else z
    }

/**
 * Gets the Y coordinate from the position.
 */
val FastVector.y: Int
    get() {
        val y = (this and Y_MASK).toInt()
        return if (y and (1 shl Y_BITS - 1) != 0) y - (1 shl Y_BITS) else y
    }

/**
 * Sets the X coordinate of the position.
 */
infix fun FastVector.withX(x: Int): FastVector = bitSetTo(x.toLong(), X_SHIFT, X_BITS)

/**
 * Sets the Z coordinate of the position.
 */
infix fun FastVector.withZ(z: Int): FastVector = bitSetTo(z.toLong(), Z_SHIFT, Z_BITS)

/**
 * Sets the Y coordinate of the position.
 */
infix fun FastVector.withY(y: Int): FastVector = bitSetTo(y.toLong(), 0, Y_BITS)

/**
 * Adds the given value to the X coordinate.
 */
infix fun FastVector.addX(value: Int): FastVector = withX(x + value)

/**
 * Adds the given value to the Z coordinate.
 */
infix fun FastVector.addZ(value: Int): FastVector = withZ(z + value)

/**
 * Adds the given value to the Y coordinate.
 */
infix fun FastVector.addY(value: Int): FastVector = withY(y + value)

/**
 * Adds the given vector to the position.
 */
infix fun FastVector.plus(vec: FastVector): FastVector = fastVectorOf(x + vec.x, y + vec.y, z + vec.z)

/**
 * Adds the given vector to the position.
 * @return The new position.
 */
infix fun FastVector.plus(vec: Vec3i): FastVector = fastVectorOf(x + vec.x, y + vec.y, z + vec.z)

/**
 * Converts a [Vec3i] to a [FastVector].
 * @return The encoded position.
 */
fun Vec3i.toFastVec(): FastVector = fastVectorOf(x.toLong(), y.toLong(), z.toLong())

/**
 * Converts a [Vec3d] to a [FastVector].
 * @return The encoded position.
 */
fun Vec3d.toFastVec(): FastVector = fastVectorOf(x.toLong(), y.toLong(), z.toLong())

/**
 * Converts the [FastVector] into a [Vec3d].
 */
fun FastVector.toVec3d(): Vec3d = Vec3d(x.toDouble(), y.toDouble(), z.toDouble())

/**
 * Converts the [FastVector] into a [Vec3i].
 */
fun FastVector.toVec3i(): Vec3i = Vec3i(x, y, z)

/**
 * Converts the [FastVector] into a [BlockPos].
 */
fun FastVector.toBlockPos(): BlockPos = BlockPos(x, y, z)
