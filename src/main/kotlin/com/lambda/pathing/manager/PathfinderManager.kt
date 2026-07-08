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
import com.lambda.pathing.movement.WalkingMovementModel
import com.lambda.pathing.refinement.PathRefiner
import com.lambda.threading.runSafe
import com.lambda.threading.runSafeAutomated
import com.lambda.util.world.FastVector
import com.lambda.util.world.toFastVec
import com.lambda.util.world.toBlockPos
import com.lambda.util.world.x
import com.lambda.util.world.y
import com.lambda.util.world.z
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
            runSafeAutomated {
                synchronizeWorldChange(event.pos)
            }
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

        val graph = LazyGraph<FastVector>(
            successorProvider = { node ->
                runSafe { with(WalkingMovementModel) { successors(node, config) } } ?: emptyMap()
            }
        )

        val handle = TraversalHandle(
            id = ++nextTraversalId,
            owner = owner,
            goal = goal,
            config = config,
        )

        val planner = DStarLite(
            graph = graph,
            start = player.blockPos.toFastVec(),
            goal = goal.targetNode,
            heuristic = ::movementHeuristic,
            // Stable tie-break between equal-cost successors so the coarse path
            // doesn't flip between runs and force a re-refinement / shortcut
            // angle change. FastVector is a Long; natural ordering is enough.
            nodeTieBreaker = naturalOrder(),
        )

        activeSession = ActiveSession(handle, graph, planner)
        computeActivePath()
        return handle
    }

    fun AutomatedSafeContext.refreshActiveTraversal(): TraversalHandle? {
        val session = activeSession ?: return null
        if (session.handle.status.isTerminal) return session.handle
        session.planner.updateStart(player.blockPos.toFastVec())
        computeActivePath()
        return session.handle
    }

    fun AutomatedSafeContext.refreshActiveTraversalIfStartMoved(): TraversalHandle? {
        val session = activeSession ?: return null
        if (session.handle.status.isTerminal) return session.handle

        val playerBlock = player.blockPos.toFastVec()
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

        val affectedNodes = WalkingMovementModel.affectedNodes(pos)
        val sync = session.planner.synchronizeAffected(affectedNodes)
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

        session.planner.updateStart(player.blockPos.toFastVec())
        computeActivePath()
        return session.handle
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
        val result = session.planner.computeShortestPath(handle.config.computeBudget.toDuration(DurationUnit.MILLISECONDS))
        val coarsePath = session.planner.path(handle.config.maxPathLength)

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
        var lastRefinementLogKey: String? = null,
        var lastCoarsePath: List<FastVector>? = null,
        var lastRefinedPath: List<FastVector>? = null,
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
     * Anisotropic admissible heuristic for the cost of traveling a -> b.
     *
     * Movement costs are strongly direction-dependent: the cheapest edge per
     * horizontal block costs [WalkingMovementModel.MIN_COST_PER_HORIZONTAL_BLOCK],
     * per ascended block [WalkingMovementModel.MIN_COST_PER_ASCENDED_BLOCK], and
     * per descended block [WalkingMovementModel.MIN_COST_PER_DESCENDED_BLOCK].
     * Any path must cover all three components, so the max of the per-component
     * lower bounds is admissible and consistent — and strictly tighter than the
     * plain Euclidean distance whenever vertical travel is involved. The caps
     * are derived from the movement model's cost table, so adding cheaper edge
     * types there keeps this heuristic admissible automatically.
     */
    private fun movementHeuristic(a: FastVector, b: FastVector): Double {
        val dx = (a.x - b.x).toDouble()
        val dz = (a.z - b.z).toDouble()
        val dy = (b.y - a.y).toDouble()
        val horizontal = sqrt(dx * dx + dz * dz) * WalkingMovementModel.MIN_COST_PER_HORIZONTAL_BLOCK
        val vertical = if (dy > 0.0) {
            dy * WalkingMovementModel.MIN_COST_PER_ASCENDED_BLOCK
        } else {
            -dy * WalkingMovementModel.MIN_COST_PER_DESCENDED_BLOCK
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
