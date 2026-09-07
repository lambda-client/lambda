package com.lambda.pathing.coarse

import com.lambda.pathing.graph.CoarseRouteCandidate
import com.lambda.pathing.graph.DStarLite
import com.lambda.pathing.graph.LazyGraph
import com.lambda.pathing.graph.TailCost
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.core.PathingChunk
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.VoxelPos
import com.lambda.pathing.movement.CoarseEdge
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * The coarse layer's D* runs over [MomentumStance] -- stance times [SpeedClass] -- so
 * the field can price momentum honestly; see [MomentumRules]. Everything public stays
 * stance-shaped: callers never meet the class dimension, they meet stance values that
 * are the best over both classes, and routes whose brake self-edges have been folded
 * away.
 */
class CoarsePlanner(

    val view: CoarseVoxelView,
    val moves: SimpleMoveLibrary,
    start: Stance,
    goal: Stance,

    private val sweepBudget: Int = 40_000,

    /** See [FrontierAnchors.NOTHING_CAPTURABLE]: capturable unknowns never anchor. */
    private val capturable: (Int, Int) -> Boolean = FrontierAnchors.NOTHING_CAPTURABLE,

    /** Reported for every capturable unknown that suppressed an anchor; see [FrontierAnchors.sweep]. */
    private val onCaptureLag: (Int, Int, Int) -> Unit = { _, _, _ -> },
) {

    private val anchors = HashMap<Stance, Double>()

    val goalStance: Stance = goal

    private val goalNode = MomentumStance(goal, SpeedClass.STOPPED)

    private val edgeCache = CoarseEdgeCache(view, moves)

    private val graph = LazyGraph(
        successorProvider = { node: MomentumStance ->
            val base = MomentumRules.successors(edgeCache, node)
            val optimistic = optimisticEdgeFrom(node)
            if (optimistic.isEmpty()) base else base + optimistic
        },
        predecessorProvider = { node: MomentumStance ->
            val base = MomentumRules.predecessors(edgeCache, node)
            val viaAnchors = if (node == goalNode) anchorPredecessors() else emptyMap()
            if (viaAnchors.isEmpty()) base else base + viaAnchors
        },
    )

    val search = DStarLite(
        graph = graph,
        start = MomentumStance(start, SpeedClass.STOPPED),
        goal = goalNode,
        heuristic = { a, b -> moves.heuristic(a.stance, b.stance) },
        nodeTieBreaker = compareBy({ it.stance.y }, { it.stance.x }, { it.stance.z }, { it.speed }),
    )

    val graphSize: Int get() = graph.size

    val graphNodes: Set<Stance> get() = graph.nodes.mapTo(HashSet()) { it.stance }

    /** Best-over-classes cost to goal, for observers that think in stances. */
    fun stanceCost(stance: Stance): Double = minOf(
        search.g(MomentumStance(stance, SpeedClass.MOVING)),
        search.g(MomentumStance(stance, SpeedClass.STOPPED)),
    )

    fun inFrontier(stance: Stance): Boolean =
        MomentumStance(stance, SpeedClass.MOVING) in search.queue ||
            MomentumStance(stance, SpeedClass.STOPPED) in search.queue

    fun knownSuccessorsOf(node: Stance): Map<Stance, Double> {
        val merged = HashMap<Stance, Double>()
        for (speed in SpeedClass.entries) {
            for ((successor, cost) in graph.knownSuccessors(MomentumStance(node, speed))) {
                if (successor.stance == node) continue
                merged.merge(successor.stance, cost, ::minOf)
            }
        }
        return merged
    }

    fun repair(
        timeBudget: Duration = 5.milliseconds,
        maxExpansions: Int = Int.MAX_VALUE,
        cancelled: () -> Boolean = { false },
    ): DStarLite.ComputeResult = search.computeShortestPath(timeBudget, maxExpansions, cancelled)

    fun updateStart(start: Stance) = search.updateStart(MomentumStance(start, SpeedClass.STOPPED))

    val optimisticAnchors: Map<Stance, Double> get() = HashMap(anchors)

    fun optimisticEdgeCost(from: Stance): Double = SpeedClass.entries.minOf {
        graph.knownSuccessors(MomentumStance(from, it))[goalNode] ?: Double.POSITIVE_INFINITY
    }

    private fun optimisticEdgeFrom(node: MomentumStance): Map<MomentumStance, Double> {
        val cost = anchors[node.stance] ?: return emptyMap()
        return mapOf(goalNode to cost)
    }

    private fun anchorPredecessors(): Map<MomentumStance, Double> {
        if (anchors.isEmpty()) return emptyMap()
        val out = LinkedHashMap<MomentumStance, Double>(anchors.size * 2)
        for ((stance, cost) in anchors) {
            out[MomentumStance(stance, SpeedClass.MOVING)] = cost
            out[MomentumStance(stance, SpeedClass.STOPPED)] = cost
        }
        return out
    }

    private fun updateAnchorEdges(anchor: Stance, cost: Double) {
        search.updateEdge(MomentumStance(anchor, SpeedClass.MOVING), goalNode, cost)
        search.updateEdge(MomentumStance(anchor, SpeedClass.STOPPED), goalNode, cost)
    }

    fun advanceFrontier(probed: Map<Stance, Double>): Boolean {
        var changed = false

        // Retirement waits for cells to be KNOWN; creation refuses merely capturable
        // unknowns. The asymmetry is deliberate: docs/decisions/anchor-lifecycle.md.
        val refused = FrontierAnchors.refusesOptimism(view, moves, goalStance)
        val retired = anchors.keys.filterTo(ArrayList()) {
            refused || !FrontierAnchors.bordersUnknown(view, it)
        }
        retired.forEach {
            anchors.remove(it)
            updateAnchorEdges(it, Double.POSITIVE_INFINITY)
            changed = true
        }

        probed.forEach { (anchor, cost) ->
            if (anchor != goalStance && anchors[anchor] != cost) {
                anchors[anchor] = cost
                updateAnchorEdges(anchor, cost)
                changed = true
            }
        }
        return changed
    }

    fun retireAllAnchors(): Boolean {
        if (anchors.isEmpty()) return false
        anchors.keys.toList().forEach { anchor ->
            anchors.remove(anchor)
            updateAnchorEdges(anchor, Double.POSITIVE_INFINITY)
        }
        return true
    }

    /**
     * Mints anchors by flooding from [from] through real edges, so every anchor is
     * reachable by construction. Optimism is a route-terminal device, never a movement;
     * see docs/decisions/anchor-lifecycle.md.
     */
    fun advanceReachableFrontier(
        from: Stance = search.start.stance,
        cancelled: () -> Boolean = { false },
    ): Boolean = advanceFrontier(
        FrontierAnchors.sweep(
            view, moves, from, goalStance, maxNodes = sweepBudget, cancelled = cancelled,
            capturable = capturable, onCaptureLag = onCaptureLag,
            edges = edgeCache::edgesFrom,
        ),
    )

    fun discoverReachableFrontier(cancelled: () -> Boolean = { false }): Boolean {
        val changed = advanceReachableFrontier(cancelled = cancelled)
        if (changed) repair(timeBudget = Duration.INFINITE, cancelled = cancelled)
        return changed
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
    ): CoarseRouteCandidate<Stance>? {
        val candidate = search.computeRouteCandidate(
            maxLength = maxLength,
            maxExpansions = ROUTE_DESCENT_EXPANSIONS,
            cancelled = cancelled,
        ) ?: return null
        // Brake self-edges fold away: consecutive nodes sharing a stance are one visit.
        val stances = ArrayList<Stance>(candidate.nodes.size)
        for (node in candidate.nodes) {
            if (stances.lastOrNull() != node.stance) stances += node.stance
        }
        return CoarseRouteCandidate(
            nodes = stances,
            ticks = candidate.ticks,
            exactFromStart = candidate.exactFromStart,
            routeVersion = candidate.routeVersion,
        )
    }

    fun routePlan(
        snapshotRevision: Long,
        maxLength: Int = 10_000,
        cancelled: () -> Boolean = { false },
    ): CoarseRoutePlan? {
        val candidate = route(maxLength, cancelled) ?: return null

        val nodes =
            if (candidate.nodes.size >= 2 && candidate.nodes.last() == goalStance &&
                candidate.nodes[candidate.nodes.size - 2] in anchors
            ) candidate.nodes.dropLast(1) else candidate.nodes
        val edges = nodes.zipWithNext { from, to -> liveEdge(from, to) ?: return null }
        return plan(snapshotRevision, nodes, edges, candidate.ticks, candidate.exactFromStart)
    }

    private fun liveEdge(from: Stance, to: Stance): CoarseEdge? =
        edgeCache.edgesFrom(from)
            .asSequence()
            .filter { it.to == to }
            .minWithOrNull(compareBy({ it.lowerBoundTicks }, { it.id.template.value }))

    fun routeFailureReport(): String = buildString {
        append("start=").append(search.start.stance)
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
        tailCosts = nodes.map { stanceTailCost(it) },
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
            val synchronized = synchronizeStances(stale)
            if (synchronized.edgesRemoved + synchronized.edgesChanged + synchronized.edgesAdded == 0) {
                return null
            }
            repair(timeBudget = Duration.INFINITE, cancelled = cancelled)
            routePlan(snapshotRevision, maxLength)?.let { return it }
        }
        return null
    }

    fun tailCost(node: Stance = search.start.stance): TailCost = stanceTailCost(node)

    /**
     * The better-informed of the two class nodes: exactness first, then cost. A raw min
     * over classes would prefer an unexplored MOVING node's bound over an exact STOPPED answer.
     */
    private fun stanceTailCost(node: Stance): TailCost {
        val moving = search.tailCost(MomentumStance(node, SpeedClass.MOVING))
        val stopped = search.tailCost(MomentumStance(node, SpeedClass.STOPPED))
        return when {
            moving is TailCost.Exact && stopped is TailCost.Exact ->
                if (moving.ticks <= stopped.ticks) moving else stopped
            moving is TailCost.Exact -> moving
            stopped is TailCost.Exact -> stopped
            moving is TailCost.Unreachable -> stopped
            stopped is TailCost.Unreachable -> moving
            else -> if (moving.lowerBound <= stopped.lowerBound) moving else stopped
        }
    }

    private fun synchronizeStances(affected: Iterable<Stance>): DStarLite.SynchronizationResult {
        // Synchronization regenerates from the live view: memoized edges go first, even
        // for callers that arrive without a world-change notification.
        edgeCache.invalidateStances(affected)
        val lifted = HashSet<MomentumStance>()
        for (stance in affected) {
            lifted += MomentumStance(stance, SpeedClass.MOVING)
            lifted += MomentumStance(stance, SpeedClass.STOPPED)
        }
        return search.synchronizeAffected(lifted)
    }

    private companion object {

        const val ROUTE_RESYNCHRONIZATION_ROUNDS = 64

        const val ROUTE_DESCENT_EXPANSIONS = 200_000
    }

    fun valueField(): CoarseValueField = CoarseValueField(
        view = view,
        moves = moves,
        label = { stance ->
            minOf(nodeLabel(stance, SpeedClass.MOVING), nodeLabel(stance, SpeedClass.STOPPED))
        },
        goal = goalStance,
        labelAt = ::nodeLabel,
        edgeProvider = edgeCache::edgesFrom,
    )

    private fun nodeLabel(stance: Stance, speed: SpeedClass): Double {
        val node = MomentumStance(stance, speed)
        return minOf(search.g(node), search.rhs(node))
    }

    fun worldChanged(changed: Iterable<VoxelPos>): DStarLite.SynchronizationResult {
        edgeCache.invalidateVoxels(changed)
        val affected = HashSet<Stance>()
        changed.forEach { affected += moves.affectedOrigins(it) }
        return synchronizeStances(affected)
    }

    fun chunksChanged(chunks: Iterable<PathingChunk>): DStarLite.SynchronizationResult {
        // Range-based cache eviction, not graph-node-filtered: the cache can hold
        // stances the graph never adopted (steering reads, rim origins).
        edgeCache.invalidateChunks(chunks)
        val affected = HashSet<Stance>()
        val nodes = graphNodes
        chunks.forEach { affected += moves.affectedOrigins(it, nodes) }
        return synchronizeStances(affected)
    }
}
