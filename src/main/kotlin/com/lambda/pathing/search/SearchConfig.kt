package com.lambda.pathing.search

import com.lambda.pathing.actions.TrajectoryDecision
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
	val stallExpansions: Int = 3000,
	val lastResortFromStart: Boolean = false,
	val exploration: Exploration? = null,
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

data class Exploration(
	val seed: Long,
	val corridorSteps: Int,
	val corridorMarginTicks: Double,
	val launchSteps: Int,
	val jitterTicks: Double,
	val taboo: TrajectoryDecision? = null,
	val seeds: List<TrajectoryDecision> = emptyList(),
	val followDepth: Int = 0,
) {
	fun jitter(stance: Any, elapsed: Int, decision: TrajectoryDecision): Double {
		var mixed = seed xor (stance.hashCode().toLong() * -0x61c8864680b583ebL) xor (elapsed.toLong() shl 32) xor decision.hashCode().toLong()
		mixed = (mixed xor (mixed ushr 33)) * -0xae502812aa7333L
		mixed = (mixed xor (mixed ushr 33)) * -0x3b314601e57a13adL
		mixed = mixed xor (mixed ushr 33)
		return jitterTicks * (mixed ushr 11).toDouble() / (1L shl 53).toDouble()
	}
}
