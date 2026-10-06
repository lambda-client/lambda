package com.lambda.pathing.search

import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.core.PathingSection

interface WorldLink {
	fun await(timeoutMillis: Long): Boolean

	fun sync(route: CoarseRoutePlan): WorldSyncResult

	fun capturable(sectionX: Int, sectionZ: Int): Boolean
}

sealed interface WorldSyncResult {
	val mutations: Set<PathingSection> get() = emptySet()

	data object Quiet : WorldSyncResult

	data class Woken(
		override val mutations: Set<PathingSection> = emptySet(),
	) : WorldSyncResult

	data class Changed(
		val route: CoarseRoutePlan?,
		override val mutations: Set<PathingSection> = emptySet(),
	) : WorldSyncResult
}
