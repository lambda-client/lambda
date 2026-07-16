/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.trajectory

import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.coarse.SimpleMoveLibrary
import com.lambda.pathing.coarse.Stance
import com.lambda.pathing.core.TailCost
import com.lambda.util.player.prediction.MovementSimulationState
import kotlin.math.hypot

/** The admissible tail selected for a continuous trajectory checkpoint. */
data class TailBound(
    val ticks: Double,
    val viaNodeIndex: Int,
    val tailCost: TailCost,
)

/**
 * Lower-bounds the remaining travel time from an exact simulated state.
 *
 * The state may be between coarse stances, so the bound considers the next few route
 * nodes and adds (a) the anisotropic lower bound from the continuous feet position to
 * that stance and (b) that node's typed cost-to-go answer. [TailCost.Bounds] contributes
 * only its admissible lower bound; an arbitrary D* label is therefore never treated as
 * exact. Taking the minimum keeps the route corridor a hint rather than forcing a
 * particular projection.
 */
fun tailLowerBound(
    state: MovementSimulationState,
    route: CoarseRoutePlan,
    fromNodeIndex: Int,
    lookAheadNodes: Int = DEFAULT_TAIL_LOOKAHEAD_NODES,
): TailBound {
    require(fromNodeIndex in route.nodes.indices) { "Route progress must name a route node" }
    require(lookAheadNodes > 0) { "Tail lookahead must be positive" }

    val last = minOf(route.nodes.lastIndex, fromNodeIndex + lookAheadNodes - 1)
    var best: TailBound? = null
    for (index in fromNodeIndex..last) {
        val tail = route.tailCostAt(index)
        val ticks = continuousHeuristic(state, route.nodes[index], route.heuristicCaps) + tail.lowerBound
        val incumbent = best
        if (incumbent == null || ticks < incumbent.ticks) {
            best = TailBound(ticks, index, tail)
        }
    }
    return checkNotNull(best)
}

private fun CoarseRoutePlan.tailCostAt(index: Int): TailCost {
    return tailCosts[index]
}

private fun continuousHeuristic(
    state: MovementSimulationState,
    to: Stance,
    caps: SimpleMoveLibrary.HeuristicCaps,
): Double {
    val dx = to.x + 0.5 - state.position.x
    val dz = to.z + 0.5 - state.position.z
    // Arrival is radius-tolerant (the stop condition accepts anywhere within the goal
    // radius), so the residual fraction of a block inside that tolerance costs zero
    // ticks. Without this slack a body already stopped at the goal carried a phantom
    // tail, and `elapsed + tail` exceeded its own tape's certified total -- the exact
    // inadmissibility the ranking must never see. Slack only loosens a lower bound.
    val horizontalBlocks = (hypot(dx, dz) - ARRIVAL_SLACK_BLOCKS).coerceAtLeast(0.0)
    val horizontal = horizontalBlocks * caps.horizontalTicksPerBlock.finiteOrZero()
    val dy = to.y - state.position.y
    val vertical = when {
        dy > 0.0 -> dy * caps.ascentTicksPerBlock.finiteOrZero()
        dy < 0.0 -> -dy * caps.descentTicksPerBlock.finiteOrZero()
        else -> 0.0
    }
    return maxOf(horizontal, vertical)
}

private fun Double.finiteOrZero(): Double = if (isFinite()) this else 0.0

const val DEFAULT_TAIL_LOOKAHEAD_NODES = 3

/** Covers the largest goal radius in use; must stay >= WalkingSeedSearchConfig.goalRadius. */
private const val ARRIVAL_SLACK_BLOCKS = 0.25
