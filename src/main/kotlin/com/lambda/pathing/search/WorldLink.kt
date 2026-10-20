package com.lambda.pathing.search

import com.lambda.pathing.actions.BodyState
import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.core.PathingChunk
import com.lambda.pathing.core.VoxelPos

interface WorldLink {
	val startRevision: Long? get() = null

	fun await(timeoutMillis: Long): Boolean

	fun sync(route: CoarseRoutePlan, from: BodyState): WorldSyncResult

	fun loadable(chunkX: Int, chunkZ: Int): Boolean

	fun obstacles(): LongArray? = null
}

sealed interface WorldSyncResult {
	val mutations: Set<VoxelPos> get() = emptySet()
	val chunks: Set<PathingChunk> get() = emptySet()
	val revision: Long? get() = null

	data object Quiet : WorldSyncResult

	data class Woken(
		override val mutations: Set<VoxelPos> = emptySet(),
		override val chunks: Set<PathingChunk> = emptySet(),
		override val revision: Long? = null,
	) : WorldSyncResult

	data class Changed(
		val route: CoarseRoutePlan?,
		override val mutations: Set<VoxelPos> = emptySet(),
		override val chunks: Set<PathingChunk> = emptySet(),
		override val revision: Long? = null,
	) : WorldSyncResult
}
