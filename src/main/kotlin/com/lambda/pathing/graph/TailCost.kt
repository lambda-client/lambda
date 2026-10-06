package com.lambda.pathing.graph

import it.unimi.dsi.fastutil.longs.LongArrayList

sealed interface TailCost {
	val lowerBound: Double

	data class Exact(val ticks: Double) : TailCost {
		override val lowerBound: Double get() = ticks
	}

	data class Bounds(
		override val lowerBound: Double,
		val upperBound: Double?,
	) : TailCost

	data object Unreachable : TailCost {
		override val lowerBound: Double = Double.POSITIVE_INFINITY
	}
}

data class CoarseRouteCandidate<N>(
	val nodes: List<N>,
	val ticks: Double,
	val exactFromStart: Boolean,
)

data class LongRouteCandidate(
	val nodes: LongArrayList,
	val ticks: Double,
	val exactFromStart: Boolean,
)
