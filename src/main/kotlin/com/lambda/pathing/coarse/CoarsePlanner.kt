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

    val view: CoarseVoxelView,
    val moves: SimpleMoveLibrary,
    start: Stance,
    goal: Stance,

    private val sweepBudget: Int = 40_000,
) {

    private val anchors = HashMap<Stance, Double>()

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

    val graphNodes: Set<Stance> get() = graph.nodes

    fun knownSuccessorsOf(node: Stance): Map<Stance, Double> = graph.knownSuccessors(node)

    fun repair(
        timeBudget: Duration = 5.milliseconds,
        maxExpansions: Int = Int.MAX_VALUE,
        cancelled: () -> Boolean = { false },
    ): DStarLite.ComputeResult = search.computeShortestPath(timeBudget, maxExpansions, cancelled)

    fun updateStart(start: Stance) = search.updateStart(start)

    val optimisticAnchors: Map<Stance, Double> get() = HashMap(anchors)

    fun optimisticEdgeCost(from: Stance): Double =
        graph.knownSuccessors(from)[search.goal] ?: Double.POSITIVE_INFINITY

    fun advanceFrontier(probed: Map<Stance, Double>): Boolean {
        var changed = false

        val refused = FrontierAnchors.refusesOptimism(view, moves, search.goal)
        val retired = anchors.keys.filterTo(ArrayList()) {
            refused || !FrontierAnchors.bordersUnknown(view, it)
        }
        retired.forEach {
            anchors.remove(it)
            search.updateEdge(it, search.goal, Double.POSITIVE_INFINITY)
            changed = true
        }

        probed.forEach { (anchor, cost) ->
            if (anchor != search.goal && anchors[anchor] != cost) {
                anchors[anchor] = cost
                search.updateEdge(anchor, search.goal, cost)
                changed = true
            }
        }
        return changed
    }

    fun retireAllAnchors(): Boolean {
        if (anchors.isEmpty()) return false
        anchors.keys.toList().forEach { anchor ->
            anchors.remove(anchor)
            search.updateEdge(anchor, search.goal, Double.POSITIVE_INFINITY)
        }
        return true
    }

    fun discoverReachableFrontier(cancelled: () -> Boolean = { false }): Boolean {
        val swept = FrontierAnchors.sweep(
            view, moves, search.start, search.goal, maxNodes = sweepBudget, cancelled = cancelled,
        )
        var changed = false
        swept.forEach { (anchor, cost) ->
            if (anchors[anchor] != cost) {
                anchors[anchor] = cost
                search.updateEdge(anchor, search.goal, cost)
                changed = true
            }
        }
        if (changed) repair(timeBudget = Duration.INFINITE, cancelled = cancelled)
        return changed
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

        const val ROUTE_RESYNCHRONIZATION_ROUNDS = 64

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
