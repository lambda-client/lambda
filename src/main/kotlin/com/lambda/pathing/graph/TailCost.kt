package com.lambda.pathing.graph

sealed interface TailCost {
	val lowerBound: Double

	data class Exact(
		val ticks: Double,
		val routeVersion: Long,
	) : TailCost {
		override val lowerBound: Double get() = ticks
	}

	data class Bounds(
		override val lowerBound: Double,
		val upperBound: Double?,
		val routeVersion: Long,
	) : TailCost

	data class Unreachable(val routeVersion: Long) : TailCost {
		override val lowerBound: Double = Double.POSITIVE_INFINITY
	}
}

data class CoarseRouteCandidate<N>(
	val nodes: List<N>,
	val ticks: Double,
	val exactFromStart: Boolean,
	val routeVersion: Long,
)

/** A greedy descent over packed long nodes; see [LongDStarLite.computeRouteCandidate]. */
data class LongRouteCandidate(
	val nodes: it.unimi.dsi.fastutil.longs.LongArrayList,
	val ticks: Double,
	val exactFromStart: Boolean,
	val routeVersion: Long,
)
