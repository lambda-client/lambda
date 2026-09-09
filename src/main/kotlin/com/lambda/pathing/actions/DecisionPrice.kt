package com.lambda.pathing.actions

data class DecisionPrice(

	val ticks: Double = 0.0,

	val difficulty: Double = 0.0,
) {
	init {
		require(ticks >= 0.0 && ticks.isFinite()) { "decision ticks must be finite and non-negative: $ticks" }
		require(difficulty in 0.0..1.0) { "difficulty is a 0..1 fraction: $difficulty" }
	}

	operator fun plus(other: DecisionPrice) = DecisionPrice(
		ticks = ticks + other.ticks,
		difficulty = maxOf(difficulty, other.difficulty),
	)

	companion object {
		val FREE = DecisionPrice()
	}
}

class PricedDecision(val decision: TrajectoryDecision, val price: DecisionPrice)
