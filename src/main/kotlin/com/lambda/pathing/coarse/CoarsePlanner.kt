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
import com.lambda.pathing.world.PathingChunk
import com.lambda.pathing.world.VoxelPos
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class CoarsePlanner(
    private val view: CoarseVoxelView,
    val moves: SimpleMoveLibrary,
    start: Stance,
    goal: Stance,
) {
    /**
     * Stances that reach the goal optimistically, with the admissible cost of the
     * unstreamed remainder. Everything else in the graph is terrain the client has.
     */
    private var anchors: Map<Stance, Double> = emptyMap()

    private val graph = LazyGraph(
        successorProvider = { node: Stance -> moves.successorCosts(view, node) + optimisticEdgeFrom(node) },
        predecessorProvider = { node: Stance ->
            if (node == goal) moves.predecessorCosts(view, node) + anchors
            else moves.predecessorCosts(view, node)
        },
    )

    val search = DStarLite(
        graph = graph,
        start = start,
        goal = goal,
        heuristic = moves::heuristic,
        nodeTieBreaker = compareBy({ it.y }, { it.x }, { it.z }),
    )

    val graphSize: Int get() = graph.size

    fun repair(
        timeBudget: Duration = 5.milliseconds,
        maxExpansions: Int = Int.MAX_VALUE,
        cancelled: () -> Boolean = { false },
    ): DStarLite.ComputeResult = search.computeShortestPath(timeBudget, maxExpansions, cancelled)

    fun updateStart(start: Stance) = search.updateStart(start)

    val optimisticAnchors: Map<Stance, Double> get() = anchors

    /** Cost the graph currently holds for the optimistic step from [from] to the goal. */
    fun optimisticEdgeCost(from: Stance): Double =
        graph.knownSuccessors(from)[search.goal] ?: Double.POSITIVE_INFINITY

    /**
     * Moves the optimistic edge to where the streamed world now ends.
     *
     * Retiring an anchor and seeding its replacement are two ordinary edge updates, so
     * the graph the walk has already paid for survives: chunk arrivals extend it
     * outward instead of replacing a fictional surface everywhere at once.
     */
    fun advanceFrontier(probed: Map<Stance, Double>): Boolean {
        if (probed == anchors) return false
        val retired = anchors.keys - probed.keys
        anchors = probed
        retired.forEach { search.updateEdge(it, search.goal, Double.POSITIVE_INFINITY) }
        probed.forEach { (anchor, cost) -> search.updateEdge(anchor, search.goal, cost) }
        return true
    }

    private fun optimisticEdgeFrom(node: Stance): Map<Stance, Double> {
        val cost = anchors[node] ?: return emptyMap()
        return mapOf(search.goal to cost)
    }

    fun expandField(
        extraTicks: Double,
        timeBudget: Duration = 50.milliseconds,
        maxExpansions: Int = Int.MAX_VALUE,
        cancelled: () -> Boolean = { false },
    ): DStarLite.ComputeResult = search.expandField(extraTicks, timeBudget, maxExpansions, cancelled)

    fun route(
        maxLength: Int = 10_000,
        cancelled: () -> Boolean = { false },
    ): CoarseRouteCandidate<Stance>? = search.computeRouteCandidate(
        maxLength = maxLength,
        maxExpansions = ROUTE_DESCENT_EXPANSIONS,
        cancelled = cancelled,
    )

    fun routePlan(
        snapshotRevision: Long,
        maxLength: Int = 10_000,
        cancelled: () -> Boolean = { false },
    ): CoarseRoutePlan? {
        val candidate = route(maxLength, cancelled) ?: return null
        // The optimistic step is guidance, never a step the body is asked to walk.
        val nodes =
            if (candidate.nodes.size >= 2 && candidate.nodes.last() == search.goal &&
                candidate.nodes[candidate.nodes.size - 2] in anchors
            ) candidate.nodes.dropLast(1) else candidate.nodes
        val edges = nodes.zipWithNext { from, to -> liveEdge(from, to) ?: return null }
        return plan(snapshotRevision, nodes, edges, candidate.ticks, candidate.exactFromStart)
    }

    private fun liveEdge(from: Stance, to: Stance): CoarseEdge? =
        moves.edgesFrom(view, from)
            .asSequence()
            .filter { it.to == to }
            .minWithOrNull(compareBy({ it.lowerBoundTicks }, { it.id.template.value }))

    /** Why publication could not turn D*'s answer into a route, for the failure log. */
    fun routeFailureReport(): String = buildString {
        append("start=").append(search.start)
        append(" g=%.1f rhs=%.1f".format(search.g(search.start), search.rhs(search.start)))
        append(" nodes=").append(graphSize)
        append(" queue=").append(search.queue.size())
        append(" descent=").append(search.routeDescentReport())
    }

    private fun plan(
        snapshotRevision: Long,
        nodes: List<Stance>,
        edges: List<CoarseEdge>,
        ticks: Double,
        exactFromStart: Boolean,
    ) = CoarseRoutePlan(
        snapshotRevision = snapshotRevision,
        routeVersion = search.routeVersion,
        nodes = nodes.toList(),
        edges = edges.toList(),
        lowerBoundTicks = ticks,
        exactFromStart = exactFromStart,
        dependencies = edges.flatMapTo(HashSet()) { it.readSet },
        tailCosts = nodes.map(search::tailCost),
        heuristicCaps = moves.heuristicCaps,
    )

    /**
     * Rebuilds the route after regenerating the edges along the one D* believes in.
     *
     * Route extraction reads cached costs but re-derives the published edge from the
     * live view, so a stance whose terrain became authoritative without its cached
     * edges being regenerated yields a converged search with no extractable route.
     * That is missing knowledge, not a missing route: resynchronize those stances,
     * repair, and ask again. Each round retires the stale edges of the candidate it
     * just rejected, so the walk terminates -- either on a route that the live view
     * agrees with, or on a candidate that no longer changes anything.
     */
    fun resynchronizedRoutePlan(
        snapshotRevision: Long,
        cancelled: () -> Boolean = { false },
        maxLength: Int = 10_000,
        maxRounds: Int = ROUTE_RESYNCHRONIZATION_ROUNDS,
    ): CoarseRoutePlan? {
        repeat(maxRounds) {
            if (cancelled()) return null
            val stale = route(maxLength)?.nodes ?: return null
            val synchronized = search.synchronizeAffected(stale)
            if (synchronized.edgesRemoved + synchronized.edgesChanged + synchronized.edgesAdded == 0) {
                return null
            }
            repair(timeBudget = Duration.INFINITE, cancelled = cancelled)
            routePlan(snapshotRevision, maxLength)?.let { return it }
        }
        return null
    }

    fun tailCost(node: Stance = search.start): TailCost = search.tailCost(node)

    private companion object {
        /** Each round retires the stale edges of one candidate; the walk is finite. */
        const val ROUTE_RESYNCHRONIZATION_ROUNDS = 64

        /** Settling what a descent trips over is local work; the cap only bounds abuse. */
        const val ROUTE_DESCENT_EXPANSIONS = 200_000
    }

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

    fun chunksChanged(chunks: Iterable<PathingChunk>): DStarLite.SynchronizationResult {
        val affected = HashSet<Stance>()
        chunks.forEach { affected += moves.affectedOrigins(it, graph.nodes) }
        return search.synchronizeAffected(affected)
    }
}
