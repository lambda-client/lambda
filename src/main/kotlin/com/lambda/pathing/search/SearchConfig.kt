package com.lambda.pathing.search

import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.core.MovementId

data class SearchConfig(
	val maxExpansions: Int = 8000,
	val safePrefixDelayMillis: Long = 250,
	val horizonCommitFrames: Int = 20,
	val horizonRunwayFrames: Int = 30,
	val localHorizonFrames: Int = 0,
	val minCommitExpansions: Int = 0,
	val maxFinalCommitFrames: Int = 0,
	val frontierPerKey: Int = 3,
	val maxTemperature: Double = 1.0,
) {
	init {
		require(maxExpansions > 0)
		require(safePrefixDelayMillis >= 0)
		require(horizonCommitFrames > 0)
		require(horizonRunwayFrames >= 0)
		require(frontierPerKey > 0)
		require(maxTemperature > 0.0 && maxTemperature <= 1.0)
	}

	fun localHorizon(route: CoarseRoutePlan): Int =
		if (localHorizonFrames > 0) localHorizonFrames + route.edges.count { it.movement == MovementId.CLIMB } * CLIMB_WINDOW_FRAMES
		else 0

	companion object {
		fun horizon(
			runwayFrames: Int = RUNWAY_FRAMES,
			commitFrames: Int = COMMIT_FRAMES,
			maxExpansions: Int = EXPANSIONS,
			bootstrapDelayMillis: Long = BOOTSTRAP_DELAY_MILLIS,
		) = SearchConfig(
			maxExpansions = minOf(maxExpansions, WINDOW_EXPANSIONS),
			safePrefixDelayMillis = bootstrapDelayMillis,
			horizonCommitFrames = commitFrames,
			horizonRunwayFrames = runwayFrames,
			localHorizonFrames = runwayFrames + commitFrames * WINDOW_COMMITS,
			minCommitExpansions = MIN_COMMIT_EXPANSIONS,
			maxFinalCommitFrames = commitFrames * FINAL_COMMITS,
		)

		private const val RUNWAY_FRAMES = 20
		private const val COMMIT_FRAMES = 20
		private const val EXPANSIONS = 2_000_000
		private const val BOOTSTRAP_DELAY_MILLIS = 200L

		private const val WINDOW_COMMITS = 3
		private const val FINAL_COMMITS = 2
		private const val WINDOW_EXPANSIONS = 120_000
		private const val MIN_COMMIT_EXPANSIONS = 400
		private const val CLIMB_WINDOW_FRAMES = 10
	}
}
