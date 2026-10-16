package com.lambda.pathing.world.store

internal class RememberedRegion(
	val regionX: Int,
	val regionZ: Int,
	private val minY: Int,
	private val height: Int,
	private val chunks: Array<Chunk?>,
) {
	class Chunk(private val words: LongArray, val overview: Array<String>, val tracked: Map<String, IntArray>, val timestamp: Long) {
		fun type(x: Int, yIndex: Int, z: Int): Int {
			val bit = (x shl 1) or (z shl 5) or (yIndex shl 9)
			return ((words[bit ushr 6] ushr (bit and 63)) and 3L).toInt()
		}
	}

	fun chunk(chunkX: Int, chunkZ: Int): Chunk? {
		if (chunkX shr 5 != regionX || chunkZ shr 5 != regionZ) return null
		return chunks[((chunkX and 31) shl 5) or (chunkZ and 31)]
	}

	inline fun forEachChunk(visit: (chunkX: Int, chunkZ: Int, chunk: Chunk) -> Unit) {
		for (index in 0 until REGION_CHUNKS) {
			val chunk = chunkAt(index) ?: continue
			visit((regionX shl 5) + (index ushr 5), (regionZ shl 5) + (index and 31), chunk)
		}
	}

	fun chunkAt(index: Int): Chunk? = chunks[index]

	fun surfaceY(chunk: Chunk, x: Int, z: Int): Int? {
		for (yIndex in height - 1 downTo 0) if (chunk.type(x, yIndex, z) != AIR) return yIndex + minY
		return null
	}

	fun trackedY(packed: Int): Int = (packed ushr 8) + minY

	companion object {
		const val AIR = 0

		const val SOLID = 3

		const val REGION_WIDTH = 32

		const val REGION_CHUNKS = REGION_WIDTH * REGION_WIDTH

		fun pack(x: Int, yIndex: Int, z: Int): Int = x or (z shl 4) or (yIndex shl 8)

		fun trackedX(packed: Int): Int = packed and 15

		fun trackedZ(packed: Int): Int = (packed ushr 4) and 15
	}
}
