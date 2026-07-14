/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.coarse

import com.lambda.pathing.core.CoarseRouteCandidate
import com.lambda.pathing.core.DStarLite
import com.lambda.pathing.core.LazyGraph
import com.lambda.pathing.core.TailCost
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.VoxelPos
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Thin typed boundary around the reusable D* Lite engine. */
class CoarsePlanner(
    private val view: CoarseVoxelView,
    val moves: SimpleMoveLibrary,
    start: Stance,
    goal: Stance,
) {
    private val graph = LazyGraph(
        successorProvider = { node: Stance -> moves.successorCosts(view, node) },
        predecessorProvider = { node: Stance -> moves.predecessorCosts(view, node) },
    )

    val search = DStarLite(
        graph = graph,
        start = start,
        goal = goal,
        heuristic = moves::heuristic,
        nodeTieBreaker = compareBy<Stance>({ it.y }, { it.x }, { it.z }),
    )

    fun repair(
        timeBudget: Duration = 5.milliseconds,
        maxExpansions: Int = Int.MAX_VALUE,
    ): DStarLite.ComputeResult = search.computeShortestPath(timeBudget, maxExpansions)

    fun updateStart(start: Stance) = search.updateStart(start)

    fun route(maxLength: Int = 10_000): CoarseRouteCandidate<Stance>? = search.routeCandidate(maxLength)

    fun tailCost(): TailCost = search.tailCost()

    /** Re-evaluates only known stance origins whose exact template reads overlap a changed voxel. */
    fun worldChanged(changed: Iterable<VoxelPos>): DStarLite.SynchronizationResult {
        val affected = HashSet<Stance>()
        changed.forEach { affected += moves.affectedOrigins(it) }
        return search.synchronizeAffected(affected)
    }
}
