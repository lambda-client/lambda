package com.lambda.pathing.search

data class TrajectoryRank(
	val certifiedAndSafe: Boolean,
	val certifiedHorizon: Int,
	val elapsedPlusTail: Double,
	val collisionEvents: Int,
	val launchMargin: Int,
	val inputSwitches: Int,
) : Comparable<TrajectoryRank> {
	init {
		require(elapsedPlusTail >= 0.0 && !elapsedPlusTail.isNaN())
		require(collisionEvents >= 0)
		require(launchMargin >= 0)
		require(inputSwitches >= 0)
	}

	override fun compareTo(other: TrajectoryRank): Int {
		if (certifiedAndSafe != other.certifiedAndSafe) return if (certifiedAndSafe) -1 else 1
		certifiedHorizon.compareTo(other.certifiedHorizon).let { if (it != 0) return -it }
		elapsedPlusTail.compareTo(other.elapsedPlusTail).let { if (it != 0) return it }
		collisionEvents.compareTo(other.collisionEvents).let { if (it != 0) return it }
		launchMargin.compareTo(other.launchMargin).let { if (it != 0) return -it }
		return inputSwitches.compareTo(other.inputSwitches)
	}
}
