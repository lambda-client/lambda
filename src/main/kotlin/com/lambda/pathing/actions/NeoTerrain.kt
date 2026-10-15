package com.lambda.pathing.actions

class NeoTerrain(
	val minForward: Int,
	val minSide: Int,
	val minUp: Int,
	val forwardSize: Int,
	val sideSize: Int,
	val upSize: Int,
	private val cells: ByteArray,
) {
	private val hash = cells.contentHashCode()

	private val columns = IntArray(forwardSize * sideSize).also { masks ->
		for (u in 0 until upSize) for (s in 0 until sideSize) for (f in 0 until forwardSize) {
			if (cells[(u * sideSize + s) * forwardSize + f] != CLEAR) masks[s * forwardSize + f] = masks[s * forwardSize + f] or (1 shl u)
		}
	}

	fun occupiedLevels(forward: Int, side: Int, fromUp: Int, toUp: Int): Boolean {
		val f = forward - minForward
		val s = side - minSide
		if (f < 0 || f >= forwardSize || s < 0 || s >= sideSize) return true
		val low = fromUp - minUp
		val high = toUp - minUp
		if (low < 0 || high >= upSize) return true
		val levels = (-1 ushr (31 - high + low)) shl low
		return columns[s * forwardSize + f] and levels != 0
	}

	fun at(forward: Int, side: Int, up: Int): Byte {
		val f = forward - minForward
		val s = side - minSide
		val u = up - minUp
		if (f < 0 || f >= forwardSize || s < 0 || s >= sideSize || u < 0 || u >= upSize) return BLOCK
		return cells[(u * sideSize + s) * forwardSize + f]
	}

	override fun equals(other: Any?): Boolean =
		other is NeoTerrain && other.hash == hash && other.minForward == minForward && other.minSide == minSide && other.minUp == minUp &&
				other.forwardSize == forwardSize && other.sideSize == sideSize && other.upSize == upSize && other.cells.contentEquals(cells)

	override fun hashCode(): Int = hash

	override fun toString(): String = "NeoTerrain(${forwardSize}x${sideSize}x$upSize)"

	companion object {
		const val CLEAR: Byte = 0

		const val FLOOR: Byte = 1

		const val BLOCK: Byte = 2

		fun of(minForward: Int, minSide: Int, minUp: Int, forwardSize: Int, sideSize: Int, upSize: Int, classify: (forward: Int, side: Int, up: Int) -> Byte): NeoTerrain {
			val cells = ByteArray(forwardSize * sideSize * upSize)
			for (u in 0 until upSize) for (s in 0 until sideSize) for (f in 0 until forwardSize) {
				cells[(u * sideSize + s) * forwardSize + f] = classify(minForward + f, minSide + s, minUp + u)
			}
			return NeoTerrain(minForward, minSide, minUp, forwardSize, sideSize, upSize, cells)
		}
	}
}
