package com.lambda.pathing.search

import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.core.PathingSection
import com.lambda.pathing.actions.BodyState

interface WorldLink {
	fun await(timeoutMillis: Long): Boolean

	fun sync(route: CoarseRoutePlan, from: BodyState): WorldSyncResult

	fun capturable(sectionX: Int, sectionZ: Int): Boolean
}

sealed interface WorldSyncResult {
	val mutations: Set<PathingSection> get() = emptySet()
	val revision: Long? get() = null

	data object Quiet : WorldSyncResult

	data class Woken(
		override val mutations: Set<PathingSection> = emptySet(),
		override val revision: Long? = null,
	) : WorldSyncResult

	data class Changed(
		val route: CoarseRoutePlan?,
		override val mutations: Set<PathingSection> = emptySet(),
		override val revision: Long? = null,
	) : WorldSyncResult
}
