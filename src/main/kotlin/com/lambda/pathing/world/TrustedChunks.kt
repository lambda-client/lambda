package com.lambda.pathing.world

import kotlin.math.abs

/**
 * Which chunks the client can be trusted to have fully received: loaded AND inside view
 * distance after a [CHUNK_FILTER_EDGE_MARGIN] shave. [isTrusted] reads the source live
 * (client thread); [contains] reads the set published by the last [refresh] and is safe
 * from any thread. See docs/decisions/world-capture.md.
 */
class TrustedChunks(private val source: CaptureSource) {
	@Volatile
	private var published: Set<Long> = emptySet()
	private var refreshTick = 0

	fun contains(chunkX: Int, chunkZ: Int): Boolean = chunkKey(chunkX, chunkZ) in published

	fun isTrusted(chunkX: Int, chunkZ: Int): Boolean {
		if (!source.isChunkLoaded(chunkX, chunkZ)) return false
		val centerX = source.playerChunkX
		val centerZ = source.playerChunkZ
		val viewDistance = source.viewDistance
		val dx = maxOf(0, abs(chunkX - centerX) - CHUNK_FILTER_EDGE_MARGIN).toLong()
		val dz = maxOf(0, abs(chunkZ - centerZ) - CHUNK_FILTER_EDGE_MARGIN).toLong()
		return dx * dx + dz * dz < viewDistance.toLong() * viewDistance
	}

	/** Once per capture tick; republishes every [TRUSTED_REFRESH_TICKS] ticks. */
	fun tick() {
		if (refreshTick++ % TRUSTED_REFRESH_TICKS == 0) refresh()
	}

	fun refresh() {
		val centerX = source.playerChunkX
		val centerZ = source.playerChunkZ
		val view = source.viewDistance
		val fresh = HashSet<Long>()
		for (cx in (centerX - view - 2)..(centerX + view + 2)) {
			for (cz in (centerZ - view - 2)..(centerZ + view + 2)) {
				if (isTrusted(cx, cz)) fresh += chunkKey(cx, cz)
			}
		}
		published = fresh
	}

	private fun chunkKey(chunkX: Int, chunkZ: Int): Long =
		(chunkX.toLong() and 0xFFFFFFFFL) or ((chunkZ.toLong() and 0xFFFFFFFFL) shl 32)

	companion object {
		const val CHUNK_FILTER_EDGE_MARGIN = 2
		const val TRUSTED_REFRESH_TICKS = 4
	}
}
