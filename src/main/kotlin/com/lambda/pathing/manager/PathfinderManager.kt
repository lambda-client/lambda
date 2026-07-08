/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.pathing.manager

import com.lambda.Lambda.LOG
import com.lambda.config.automation.IMutableAutomationConfig
import com.lambda.config.automation.MutableAutomationConfig
import com.lambda.config.blocks.PlannerConfig
import com.lambda.context.AutomatedSafeContext
import com.lambda.config.blocks.PathRefinementConfig
import com.lambda.core.Loadable
import com.lambda.event.events.TickEvent
import com.lambda.event.events.WorldEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.pathing.core.DStarLite
import com.lambda.pathing.core.Key
import com.lambda.pathing.core.LazyGraph
import com.lambda.pathing.goal.TraversalGoal
import com.lambda.pathing.metrics.PlannerMetrics
import com.lambda.pathing.primitives.MoveTable
import com.lambda.pathing.refinement.PathRefiner
import com.lambda.threading.runSafeAutomated
import com.lambda.util.world.FastVector
import com.lambda.util.world.toFastVec
import com.lambda.util.world.toBlockPos
import com.lambda.util.world.x
import com.lambda.util.world.y
import com.lambda.util.world.z
import com.lambda.worldview.SnapshotWorldView
import net.minecraft.util.math.BlockPos
import kotlin.math.sqrt
import kotlin.time.DurationUnit

import kotlin.time.toDuration

object PathfinderManager : Loadable,
    IMutableAutomationConfig by MutableAutomationConfig() {
    private var nextTraversalId = 0
    private var activeSession: ActiveSession? = null

    val activeTraversal: TraversalHandle?
        get() = activeSession?.handle

    init {
        listen<WorldEvent.BlockUpdate.Client>(alwaysListen = true) { event ->
            if (event.oldState == event.newState) return@listen
            // Snapshot write-through must precede graph resynchronization:
            // synchronizeAffected regenerates edges by reading the view.
            activeSession?.view?.applyObserved(event.pos, event.newState)
            runSafeAutomated {
                synchronizeWorldChange(event.pos)
            }
        }

        listen<WorldEvent.ChunkEvent.Load>(alwaysListen = true) { event ->
            activeSession?.view?.evictChunk(event.chunk.pos)
        }

        listen<WorldEvent.ChunkEvent.Unload>(alwaysListen = true) { event ->
            // Parity with live reads, which turn to air on unload. Keeping
            // observed terrain across unloads is WP7 (lifelong memory) work.
            activeSession?.view?.evictChunk(event.chunk.pos)
        }

        listen<TickEvent.Player.Post>(alwaysListen = true) {
            runSafeAutomated {
                refreshActiveTraversalIfStartMoved()
            }
        }
    }

    fun AutomatedSafeContext.requestTraversal(
        goal: TraversalGoal,
        owner: Any? = null,
        config: PlannerConfig = plannerConfig,
    ): TraversalHandle {
        activeSession?.handle?.cancel()

        // The session's base world view: a copy-on-read snapshot the block
        // update listener writes observed changes through. All plan-time
        // world reads go through it (WP1).
        val view = SnapshotWorldView(world)

        // The enabled primitive set: templates plus everything derived from
        // them — heuristic caps and invalidation extents included (WP2).
        val moves = MoveTable.build(config)
        // Execution-feedback overlay: edges the executor reported as blocked
        // in the real world get their cost multiplied, so replans learn to
        // route around them even though the world model still believes in
        // them. Session-scoped — a fresh traversal starts unprejudiced.
        val edgePenalties = HashMap<Long, Double>()
        fun penalized(from: FastVector, edges: Map<FastVector, Double>): Map<FastVector, Double> {
            if (edgePenalties.isEmpty()) return edges
            var result: HashMap<FastVector, Double>? = null
            edges.forEach { (to, cost) ->
                edgePenalties[edgeKey(from, to)]?.let { factor ->
                    (result ?: HashMap(edges).also { result = it })[to] = cost * factor
                }
            }
            return result ?: edges
        }

        val graph = LazyGraph<FastVector>(
            successorProvider = { node ->
                penalized(node, moves.successors(view, node))
            },
            // True inverse enumeration — never default to the symmetric
            // assumption: it mirrors legal step-downs into illegal step-ups
            // (e.g. under low ceilings) with the wrong cost attached.
            predecessorProvider = { node ->
                val edges = moves.predecessors(view, node)
                if (edgePenalties.isEmpty()) edges
                else edges.mapValues { (from, cost) -> cost * (edgePenalties[edgeKey(from, node)] ?: 1.0) }
            },
        )

        val handle = TraversalHandle(
            id = ++nextTraversalId,
            owner = owner,
            goal = goal,
            config = config,
        )

        val planner = DStarLite(
            graph = graph,
            start = currentStablePlannerNode(view) ?: player.blockPos.toFastVec(),
            goal = goal.targetNode,
            heuristic = { a, b -> movementHeuristic(moves.caps, a, b) },
            // Stable tie-break between equal-cost successors so the coarse path
            // doesn't flip between runs and force a re-refinement / shortcut
            // angle change. FastVector is a Long; natural ordering is enough.
            nodeTieBreaker = naturalOrder(),
        )

        activeSession = ActiveSession(handle, graph, planner, edgePenalties, view, moves)
        PlannerMetrics.sink.planStart(handle.id, planner.start, goal.targetNode)
        computeActivePath()
        return handle
    }

    fun AutomatedSafeContext.refreshActiveTraversal(): TraversalHandle? {
        val session = activeSession ?: return null
        if (session.handle.status.isTerminal) return session.handle
        val stableNode = currentStablePlannerNode() ?: return session.handle
        session.planner.updateStart(stableNode)
        computeActivePath()
        return session.handle
    }

    fun AutomatedSafeContext.refreshActiveTraversalIfStartMoved(): TraversalHandle? {
        val session = activeSession ?: return null
        if (session.handle.status.isTerminal) return session.handle

        val playerBlock = currentStablePlannerNode() ?: return session.handle
        if (playerBlock == session.handle.goal.targetNode) {
            completeActiveTraversal()
            return session.handle
        }

        val planner = session.planner
        if (playerBlock == planner.start) return session.handle

        val refined = session.lastRefinedPath
        if (refined.isNullOrEmpty()) {
            planner.updateStart(playerBlock)
            computeActivePath()
            return session.handle
        }

        val plannerIndex = refined.indexOf(planner.start)
        val searchFrom = (plannerIndex + 1).coerceAtLeast(0)
        if (searchFrom >= refined.size) return session.handle

        val playerIndexInTail = refined.subList(searchFrom, refined.size).indexOf(playerBlock)
        if (playerIndexInTail < 0) {
            // Player is between refined nodes (on a diagonal shortcut). Don't
            // touch planner.start; the cached refined path is still valid.
            return session.handle
        }

        planner.updateStart(refined[searchFrom + playerIndexInTail])
        computeActivePath()
        return session.handle
    }

    fun cancelActiveTraversal(): Boolean {
        val session = activeSession ?: return false
        session.handle.cancel()
        activeSession = null
        return true
    }

    fun AutomatedSafeContext.synchronizeWorldChange(pos: BlockPos): TraversalHandle? {
        val session = activeSession ?: return null
        if (session.handle.status.isTerminal) return session.handle

        val affectedNodes = session.moves.affectedNodes(pos.x, pos.y, pos.z)
        val syncStartNanos = System.nanoTime()
        val sync = session.planner.synchronizeAffected(affectedNodes)
        PlannerMetrics.sink.syncEnd(
            traversalId = session.handle.id,
            nodesChecked = sync.nodesChecked,
            edgesAdded = sync.edgesAdded,
            edgesRemoved = sync.edgesRemoved,
            edgesChanged = sync.edgesChanged,
            wallMicros = (System.nanoTime() - syncStartNanos) / 1_000,
        )
        session.handle.lastSynchronization = TraversalHandle.SynchronizationStats(
            nodesChecked = sync.nodesChecked,
            edgesAdded = sync.edgesAdded,
            edgesRemoved = sync.edgesRemoved,
            edgesChanged = sync.edgesChanged,
        )

        if (sync.edgesAdded > 0 || sync.edgesRemoved > 0 || sync.edgesChanged > 0) {
            session.lastCoarsePath = null
            session.lastRefinedPath = null
        }

        currentStablePlannerNode()?.let { session.planner.updateStart(it) }
        computeActivePath()
        return session.handle
    }

    fun completeActiveTraversal(): Boolean {
        val session = activeSession ?: return false
        session.handle.succeed()
        return true
    }

    fun debugInfo(): String = activeSession?.handle?.debugString() ?: "No active traversal"

    fun lazyGraphSnapshot(maxNodes: Int, maxEdges: Int): LazyGraphSnapshot {
        val graph = activeSession?.graph ?: return LazyGraphSnapshot()
        val nodePositions = graph.nodes.take(maxNodes.coerceAtLeast(0))
        val nodeSet = nodePositions.toHashSet()
        val nodes = nodePositions.map { node ->
            LazyGraphSnapshot.Node(
                pos = node,
                g = activeSession?.planner?.g(node) ?: Double.POSITIVE_INFINITY,
                rhs = activeSession?.planner?.rhs(node) ?: Double.POSITIVE_INFINITY,
                key = activeSession?.planner?.key(node) ?: Key.INFINITY,
                queued = activeSession?.planner?.isQueued(node) ?: false,
                start = activeSession?.planner?.start == node,
                goal = activeSession?.planner?.goal == node,
            )
        }
        val edges = ArrayList<LazyGraphSnapshot.Edge>(maxEdges.coerceAtLeast(0))
        var edgeLimitReached = false

        for (from in nodePositions) {
            for ((to, cost) in graph.knownSuccessors(from)) {
                if (to !in nodeSet) continue
                if (edges.size >= maxEdges.coerceAtLeast(0)) {
                    edgeLimitReached = true
                    break
                }
                edges += LazyGraphSnapshot.Edge(from, to, cost)
            }
            if (edgeLimitReached) break
        }

        return LazyGraphSnapshot(
            totalNodes = graph.size,
            nodes = nodes,
            edges = edges,
            truncated = graph.size > nodes.size || edgeLimitReached,
        )
    }

    private fun AutomatedSafeContext.computeActivePath() {
        val session = activeSession ?: return
        val handle = session.handle
        if (handle.status == TraversalHandle.Status.Cancelled) return

        handle.status = TraversalHandle.Status.Planning
        val computeStartNanos = System.nanoTime()
        val result = session.planner.computeShortestPath(handle.config.computeBudget.toDuration(DurationUnit.MILLISECONDS))
        val computeWallMicros = (System.nanoTime() - computeStartNanos) / 1_000
        val coarsePath = session.planner.path(handle.config.maxPathLength)
        val reachedGoal = coarsePath.lastOrNull() == handle.goal.targetNode
        if (session.computeCount++ == 0) {
            PlannerMetrics.sink.initialPath(handle.id, result.processedNodes, computeWallMicros, result.timedOut, coarsePath.size, reachedGoal)
        } else {
            PlannerMetrics.sink.repairEnd(handle.id, result.processedNodes, computeWallMicros, result.timedOut, coarsePath.size, reachedGoal)
        }

        // If the new coarse path is just a forward-advance of the prior coarse
        // path (player walked along it without any topology change), reuse the
        // previously refined path by reference. This avoids both the cost of
        // re-refining and the downstream executor's rebuild-on-reference-change
        // which would otherwise reset segment progress and cause oscillation.
        val priorCoarse = session.lastCoarsePath
        val priorRefined = session.lastRefinedPath
        // Don't reuse the cache once the coarse path has collapsed to just the
        // goal node — the executor stops on path.size <= 1 and would never see
        // AtGoal if we kept the old long refined path.
        val structurallyUnchanged = priorCoarse != null
            && priorRefined != null
            && coarsePath.size >= 2
            && isForwardAdvance(priorCoarse, coarsePath)

        if (structurallyUnchanged) {
            handle.coarsePath = coarsePath
            handle.path = priorRefined
            handle.graphSize = session.graph.size
            handle.processedNodes += result.processedNodes
            handle.failureReason = null
            handle.status = pathStatus(result.timedOut, coarsePath, handle)
            return
        }

        val refinement = with(PathRefiner) { refine(coarsePath, refinementConfig) }
        if (refinement.stats.enabled) {
            PlannerMetrics.sink.refineEnd(
                traversalId = handle.id,
                coarseNodes = refinement.stats.coarseNodes,
                refinedNodes = refinement.stats.refinedNodes,
                shortcutChecks = refinement.stats.shortcutChecks,
                accepted = refinement.debug.acceptedCandidates,
                rejected = refinement.debug.rejectedCandidates,
                durationMs = refinement.stats.durationMs,
            )
        }
        session.lastCoarsePath = coarsePath
        session.lastRefinedPath = refinement.path

        handle.coarsePath = coarsePath
        handle.path = refinement.path
        handle.lastRefinement = refinement.stats
        handle.lastRefinementDebug = refinement.debug
        handle.graphSize = session.graph.size
        handle.processedNodes += result.processedNodes
        handle.failureReason = null
        logRefinementSummaryIfChanged(session)

        handle.status = pathStatus(result.timedOut, coarsePath, handle)
        if (handle.status == TraversalHandle.Status.Ready && coarsePath.size <= 1 && coarsePath.lastOrNull() == handle.goal.targetNode) {
            handle.succeed()
        }
    }

    private fun pathStatus(
        timedOut: Boolean,
        coarsePath: List<FastVector>,
        handle: TraversalHandle,
    ): TraversalHandle.Status = when {
        timedOut && coarsePath.isNotEmpty() -> TraversalHandle.Status.Partial
        timedOut -> TraversalHandle.Status.Partial
        coarsePath.isEmpty() -> {
            handle.failureReason = "No walking-only path found"
            TraversalHandle.Status.Failed
        }
        coarsePath.last() != handle.goal.targetNode -> TraversalHandle.Status.Partial
        else -> TraversalHandle.Status.Ready
    }

    private fun AutomatedSafeContext.currentStablePlannerNode(
        view: SnapshotWorldView? = activeSession?.view,
    ): FastVector? {
        if (!player.isOnGround) return null
        view ?: return null
        val blockPos = player.blockPos
        return if (MoveTable.isStance(view, blockPos.x, blockPos.y, blockPos.z)) blockPos.toFastVec() else null
    }

    /**
     * True if [next] equals a contiguous suffix of [prior] (i.e. the only
     * change is that some leading nodes were consumed by player movement).
     * Equal-length identical paths return true with advance = 0.
     */
    private fun isForwardAdvance(prior: List<FastVector>, next: List<FastVector>): Boolean {
        val priorSize = prior.size
        val nextSize = next.size
        if (nextSize == 0 || nextSize > priorSize) return false
        val advance = priorSize - nextSize
        for (i in 0 until nextSize) {
            if (prior[i + advance] != next[i]) return false
        }
        return true
    }

    private data class ActiveSession(
        val handle: TraversalHandle,
        val graph: LazyGraph<FastVector>,
        val planner: DStarLite<FastVector>,
        val edgePenalties: HashMap<Long, Double>,
        val view: SnapshotWorldView,
        val moves: MoveTable.MoveSet,
        var lastRefinementLogKey: String? = null,
        var lastCoarsePath: List<FastVector>? = null,
        var lastRefinedPath: List<FastVector>? = null,
        var computeCount: Int = 0,
    )

    private fun logRefinementSummaryIfChanged(session: ActiveSession) {
        val handle = session.handle
        val stats = handle.lastRefinement
        val debug = handle.lastRefinementDebug
        if (!stats.enabled) return

        val stableKey = buildString {
            append(handle.id).append('|')
            append(stats.coarseNodes).append('|')
            append(stats.refinedNodes).append('|')
            append(stats.shortcutChecks).append('|')
            append(stats.skippedCandidates).append('|')
            append(stats.budgetExhausted).append('|')
            append(debug.acceptedCandidates).append('|')
            append(debug.rejectedCandidates).append('|')
            append(debug.skippedCandidates).append('|')
            append(debug.profileAttempts).append('|')
            append(debug.acceptedByProfile.entries.sortedBy { it.key }.joinToString()).append('|')
            append(debug.rejectedByReason.entries.sortedBy { it.key }.joinToString()).append('|')
            debug.lastAttempt?.let {
                append(it.profile).append('|')
                append(it.accepted).append('|')
                append(it.reason).append('|')
                append(it.ticks).append('|')
                append("%.2f".format(it.bestRemaining))
            }
        }

        if (session.lastRefinementLogKey == stableKey) return
        session.lastRefinementLogKey = stableKey

        LOG.info(buildString {
            appendLine("[Pathfinder] Refinement #${handle.id}")
            appendLine("  coarse=${stats.coarseNodes} refined=${stats.refinedNodes} removed=${stats.removedNodes} coarseLen=${"%.2f".format(stats.coarseLength)} refinedLen=${"%.2f".format(stats.refinedLength)} saved=${"%.2f".format(stats.savedLength)} (${ "%.1f".format(stats.savedPercent) }%)")
            appendLine("  checks=${stats.shortcutChecks} skipped=${stats.skippedCandidates} accepted=${debug.acceptedCandidates} rejected=${debug.rejectedCandidates} attempts=${debug.profileAttempts} duration=${"%.2f".format(stats.durationMs)}ms${if (stats.budgetExhausted) " budget" else ""}")
            if (debug.acceptedByProfile.isNotEmpty()) {
                appendLine("  acceptedBy=${debug.acceptedByProfile.entries.sortedByDescending { it.value }.joinToString { "${it.key} ${it.value}" }}")
            }
            if (debug.rejectedByReason.isNotEmpty()) {
                appendLine("  rejectedBy=${debug.rejectedByReason.entries.sortedByDescending { it.value }.joinToString { "${it.key} ${it.value}" }}")
            }
            debug.lastAttempt?.let {
                appendLine("  lastAttempt=${it.profile} ${if (it.accepted) "ok" else "fail"} ${it.reason} ticks=${it.ticks} left=${"%.2f".format(it.bestRemaining)} from=${it.from.toBlockPos().toShortString()} to=${it.to.toBlockPos().toShortString()}")
            }
        }.trimEnd())
    }

    /**
     * Execution feedback (research plan §4.6): the executor observed that a
     * planned edge cannot actually be traversed. Multiply its cost so repair
     * reroutes around it; repeated reports compound. The graph "learns" from
     * execution without mutating the world model.
     */
    fun AutomatedSafeContext.reportEdgeObstructed(from: FastVector, to: FastVector): Boolean {
        val session = activeSession ?: return false
        if (session.handle.status.isTerminal) return false

        val key = edgeKey(from, to)
        val factor = ((session.edgePenalties[key] ?: 1.0) * OBSTRUCTION_PENALTY_FACTOR)
            .coerceAtMost(MAX_OBSTRUCTION_PENALTY)
        session.edgePenalties[key] = factor
        PlannerMetrics.sink.edgeObstructed(session.handle.id, from, to, factor)
        LOG.info("[Pathfinder] Edge obstructed ${from.toBlockPos().toShortString()} -> ${to.toBlockPos().toShortString()}, penalty x${"%.0f".format(factor)}")

        session.lastCoarsePath = null
        session.lastRefinedPath = null
        session.planner.synchronizeAffected(setOf(from, to))
        currentStablePlannerNode()?.let { session.planner.updateStart(it) }
        computeActivePath()
        return true
    }

    /** Packs two FastVectors (Longs) into one map key without allocation. */
    private fun edgeKey(from: FastVector, to: FastVector): Long =
        from * 31 + to

    private const val OBSTRUCTION_PENALTY_FACTOR = 8.0
    private const val MAX_OBSTRUCTION_PENALTY = 4096.0

    /**
     * Anisotropic admissible heuristic for the cost of traveling a -> b.
     *
     * Movement costs are strongly direction-dependent, so each displacement
     * axis class gets its own lower bound and the max of the three is taken —
     * admissible and consistent, and strictly tighter than plain Euclidean
     * whenever vertical travel is involved (see theory note T1). The [caps]
     * are derived from the movement model's enabled move table for this
     * traversal's config, so cheaper edge types (drops, jumps) can never
     * silently make the heuristic overestimate.
     */
    private fun movementHeuristic(caps: MoveTable.HeuristicCaps, a: FastVector, b: FastVector): Double {
        val dx = (a.x - b.x).toDouble()
        val dz = (a.z - b.z).toDouble()
        val dy = (b.y - a.y).toDouble()
        val horizontal = sqrt(dx * dx + dz * dz) * caps.minCostPerHorizontalBlock
        val vertical = if (dy > 0.0) {
            dy * caps.minCostPerAscendedBlock
        } else {
            -dy * caps.minCostPerDescendedBlock
        }
        return maxOf(horizontal, vertical)
    }

    data class LazyGraphSnapshot(
        val totalNodes: Int = 0,
        val nodes: List<Node> = emptyList(),
        val edges: List<Edge> = emptyList(),
        val truncated: Boolean = false,
    ) {
        data class Node(
            val pos: FastVector,
            val g: Double,
            val rhs: Double,
            val key: Key,
            val queued: Boolean,
            val start: Boolean,
            val goal: Boolean,
        )

        data class Edge(
            val from: FastVector,
            val to: FastVector,
            val cost: Double,
        )
    }
}
