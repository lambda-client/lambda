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

    /**
     * Freezes the current route and its exact template dependencies. D* maps,
     * queues, and the live view never cross the worker/publication boundary.
     */
    fun routePlan(snapshotRevision: Long, maxLength: Int = 10_000): CoarseRoutePlan? {
        val candidate = route(maxLength) ?: return null
        val edges = candidate.nodes.zipWithNext { from, to ->
            moves.edgesFrom(view, from)
                .asSequence()
                .filter { it.to == to }
                .minWithOrNull(compareBy<CoarseEdge>({ it.lowerBoundTicks }, { it.id.template.value }))
                ?: return null
        }
        return CoarseRoutePlan(
            snapshotRevision = snapshotRevision,
            routeVersion = candidate.routeVersion,
            nodes = candidate.nodes.toList(),
            edges = edges,
            lowerBoundTicks = candidate.ticks,
            exactFromStart = candidate.exactFromStart,
            dependencies = edges.flatMapTo(HashSet()) { it.readSet },
        )
    }

    fun tailCost(): TailCost = search.tailCost()

    /** Re-evaluates only known stance origins whose exact template reads overlap a changed voxel. */
    fun worldChanged(changed: Iterable<VoxelPos>): DStarLite.SynchronizationResult {
        val affected = HashSet<Stance>()
        changed.forEach { affected += moves.affectedOrigins(it) }
        return search.synchronizeAffected(affected)
    }
}
