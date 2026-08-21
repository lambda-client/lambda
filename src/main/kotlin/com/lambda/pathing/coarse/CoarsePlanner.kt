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
        nodeTieBreaker = compareBy({ it.y }, { it.x }, { it.z }),
    )

    fun repair(
        timeBudget: Duration = 5.milliseconds,
        maxExpansions: Int = Int.MAX_VALUE,
    ): DStarLite.ComputeResult = search.computeShortestPath(timeBudget, maxExpansions)

    fun updateStart(start: Stance) = search.updateStart(start)

    fun expandField(
        extraTicks: Double,
        timeBudget: Duration = 50.milliseconds,
        maxExpansions: Int = Int.MAX_VALUE,
    ): DStarLite.ComputeResult = search.expandField(extraTicks, timeBudget, maxExpansions)

    fun route(maxLength: Int = 10_000): CoarseRouteCandidate<Stance>? = search.routeCandidate(maxLength)

    fun routePlan(snapshotRevision: Long, maxLength: Int = 10_000): CoarseRoutePlan? {
        val candidate = route(maxLength) ?: return null
        val edges = candidate.nodes.zipWithNext { from, to ->
            moves.edgesFrom(view, from)
                .asSequence()
                .filter { it.to == to }
                .minWithOrNull(compareBy({ it.lowerBoundTicks }, { it.id.template.value }))
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
            tailCosts = candidate.nodes.map(search::tailCost),
            heuristicCaps = moves.heuristicCaps,
        )
    }

    fun tailCost(node: Stance = search.start): TailCost = search.tailCost(node)

    fun valueField(): CoarseValueField = CoarseValueField(
        view = view,
        moves = moves,
        label = { stance -> minOf(search.g(stance), search.rhs(stance)) },
        goal = search.goal,
    )

    fun worldChanged(changed: Iterable<VoxelPos>): DStarLite.SynchronizationResult {
        val affected = HashSet<Stance>()
        changed.forEach { affected += moves.affectedOrigins(it) }
        return search.synchronizeAffected(affected)
    }
}
