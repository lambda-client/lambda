package com.lambda.pathing.world.streaming

import com.lambda.pathing.core.PathingChunk
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import kotlin.math.abs

class TrustedChunks(private val source: CaptureSource) {
	@Volatile
	private var published = LongOpenHashSet()
	private var refreshTick = 0

	fun contains(chunkX: Int, chunkZ: Int): Boolean = PathingChunk.key(chunkX, chunkZ) in published

	fun isTrusted(chunkX: Int, chunkZ: Int): Boolean {
		if (!source.isChunkLoaded(chunkX, chunkZ)) return false
		val centerX = source.playerChunkX
		val centerZ = source.playerChunkZ
		val viewDistance = source.viewDistance
		val dx = maxOf(0, abs(chunkX - centerX) - CHUNK_FILTER_EDGE_MARGIN).toLong()
		val dz = maxOf(0, abs(chunkZ - centerZ) - CHUNK_FILTER_EDGE_MARGIN).toLong()
		return dx * dx + dz * dz < viewDistance.toLong() * viewDistance
	}

	fun tick() {
		if (refreshTick++ % TRUSTED_REFRESH_TICKS == 0) refresh()
	}

	private fun refresh() {
		val centerX = source.playerChunkX
		val centerZ = source.playerChunkZ
		val view = source.viewDistance
		val fresh = LongOpenHashSet()
		val reach = view + CHUNK_FILTER_EDGE_MARGIN
		for (cx in (centerX - reach)..(centerX + reach)) {
			for (cz in (centerZ - reach)..(centerZ + reach)) {
				if (isTrusted(cx, cz)) fresh += PathingChunk.key(cx, cz)
			}
		}
		published = fresh
	}

	companion object {
		const val CHUNK_FILTER_EDGE_MARGIN = 2
		const val TRUSTED_REFRESH_TICKS = 4
	}
}
