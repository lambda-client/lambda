package com.lambda.pathing.search

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
}
