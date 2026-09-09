package com.lambda.pathing.coarse

import com.lambda.pathing.core.Stance

object PackedStance {
	const val Y_BITS = 10
	const val XZ_BITS = 26

	const val Y_SHIFT = 53
	const val X_SHIFT = 27
	const val Z_SHIFT = 1

	const val Y_BIAS = 1 shl (Y_BITS - 1)
	const val XZ_BIAS = 1 shl (XZ_BITS - 1)

	val Y_RANGE: IntRange = -Y_BIAS until Y_BIAS
	val XZ_RANGE: IntRange = -XZ_BIAS until XZ_BIAS

	private const val Y_MASK = (1L shl Y_BITS) - 1
	private const val XZ_MASK = (1L shl XZ_BITS) - 1
	private const val SPEED_MASK = 1L

	fun pack(x: Int, y: Int, z: Int, speed: SpeedClass): Long = pack(x, y, z, speed.ordinal)

	fun pack(stance: Stance, speed: SpeedClass): Long = pack(stance.x, stance.y, stance.z, speed.ordinal)

	fun pack(x: Int, y: Int, z: Int, speedBit: Int): Long {
		require(y in Y_RANGE) { "y out of packed range: $y" }
		require(x in XZ_RANGE) { "x out of packed range: $x" }
		require(z in XZ_RANGE) { "z out of packed range: $z" }
		require(speedBit == 0 || speedBit == 1) { "speed bit must be 0 or 1: $speedBit" }
		return ((y + Y_BIAS).toLong() shl Y_SHIFT) or
				((x + XZ_BIAS).toLong() shl X_SHIFT) or
				((z + XZ_BIAS).toLong() shl Z_SHIFT) or
				speedBit.toLong()
	}

	fun unpackX(node: Long): Int = ((node ushr X_SHIFT) and XZ_MASK).toInt() - XZ_BIAS

	fun unpackY(node: Long): Int = ((node ushr Y_SHIFT) and Y_MASK).toInt() - Y_BIAS

	fun unpackZ(node: Long): Int = ((node ushr Z_SHIFT) and XZ_MASK).toInt() - XZ_BIAS

	fun speedBit(node: Long): Int = (node and SPEED_MASK).toInt()

	fun speed(node: Long): SpeedClass = if (node and SPEED_MASK == 0L) SpeedClass.STOPPED else SpeedClass.MOVING

	fun stance(node: Long): Stance = Stance(unpackX(node), unpackY(node), unpackZ(node))

	fun withSpeed(node: Long, speed: SpeedClass): Long = (node and SPEED_MASK.inv()) or speed.ordinal.toLong()

	fun sameStance(a: Long, b: Long): Boolean = (a ushr 1) == (b ushr 1)

	fun describe(node: Long): String =
		"(${unpackX(node)}, ${unpackY(node)}, ${unpackZ(node)}, ${speed(node)})"
}
