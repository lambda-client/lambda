/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.core

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
