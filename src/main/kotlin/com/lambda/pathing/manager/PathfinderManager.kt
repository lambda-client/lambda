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
import com.lambda.pathing.maneuver.ManeuverDiscovery
import com.lambda.pathing.primitives.MoveTable
import com.lambda.pathing.refinement.PathRefiner
import com.lambda.threading.runSafeAutomated
import com.lambda.util.player.prediction.PlayerPhysicsProfile
import com.lambda.util.world.FastVector
import com.lambda.util.world.toFastVec
import com.lambda.util.world.toBlockPos
import com.lambda.util.world.x
import com.lambda.util.world.y
import com.lambda.util.world.z
import com.lambda.worldview.SnapshotWorldView
import net.minecraft.util.math.BlockPos
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.sqrt
import kotlin.time.DurationUnit

import kotlin.time.toDuration

object PathfinderManager : Loadable,
    IMutableAutomationConfig by MutableAutomationConfig() {
    private var nextTraversalId = 0
    /** All graph/discovery/D* state is confined to this one worker. */
    private val plannerWorker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "Lambda-Pathfinder").apply { isDaemon = true }
    }
    @Volatile private var activeSession: ActiveSession? = null

    val activeTraversal: TraversalHandle?
        get() = activeSession?.handle

    init {
        listen<WorldEvent.BlockUpdate.Client>(alwaysListen = true) { event ->
            if (event.oldState == event.newState) return@listen
            // Snapshot write-through must precede graph resynchronization:
            // synchronizeAffected regenerates edges by reading the view.
            activeSession?.view?.applyObserved(event.pos, event.newState)
            // event.pos can be a MUTABLE BlockPos the packet handler reuses
            // (chunk-delta updates). Anything crossing to the planner worker
            // must be snapshotted NOW — a captured reference reads whatever
            // the handler mutated it to last (observed: a 33-block wall fill
            // reached the worker as 3 stale positions × 15 repeats, so the
            // wall-crossing edges were never resynced and the planner kept
            // routing through the wall).
            runSafeAutomated { synchronizeWorldChange(event.pos.toImmutable()) }
        }

        listen<WorldEvent.ChunkEvent.Load>(alwaysListen = true) { event ->
            activeSession?.let { session ->
                val evictedSections = session.view.evictChunk(event.chunk.pos)
                if (evictedSections > 0 && session.chunkTransitionAffectsActiveSearch(event.chunk.pos)) {
                    session.chunkTopologyDirty.set(true)
                    PlannerMetrics.sink.chunkVisibility(
                        session.handle.id, event.chunk.pos.x, event.chunk.pos.z,
                        loaded = true, evictedSections = evictedSections,
                    )
                }
            }
        }

        listen<WorldEvent.ChunkEvent.Unload>(alwaysListen = true) { event ->
            // Unloaded terrain becomes conservative unknown. Keeping observed
            // terrain across unloads is WP7 (lifelong memory) work.
            activeSession?.let { session ->
                val evictedSections = session.view.evictChunk(event.chunk.pos)
                if (evictedSections > 0 && session.chunkTransitionAffectsActiveSearch(event.chunk.pos)) {
                    session.chunkTopologyDirty.set(true)
                    PlannerMetrics.sink.chunkVisibility(
                        session.handle.id, event.chunk.pos.x, event.chunk.pos.z,
                        loaded = false, evictedSections = evictedSections,
                    )
                }
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
        val edgePenalties = HashMap<EdgeKey, Double>()
        val approachRejectedEdges = HashSet<EdgeKey>()
        fun penalized(from: FastVector, edges: Map<FastVector, Double>): Map<FastVector, Double> {
            if (edgePenalties.isEmpty() && approachRejectedEdges.isEmpty()) return edges
            var result: HashMap<FastVector, Double>? = null
            edges.forEach { (to, cost) ->
                val edge = EdgeKey(from, to)
                if (edge in approachRejectedEdges) {
                    (result ?: HashMap(edges).also { result = it }).remove(to)
                } else edgePenalties[edge]?.let { factor ->
                    (result ?: HashMap(edges).also { result = it })[to] = cost * factor
                }
            }
            return result ?: edges
        }

        // WP3.2 landing-anchored maneuver discovery: extra sprint-jump edges
        // proposed the first time the backward search expands a ledge node,
        // sim-validated lazily when selected onto the candidate path (LIS).
        // Provider-level merging is T2's discovery-monotone model — edges
        // only ever appear, through the same lazy machinery as template
        // edges. The discovery object is worker-confined and simulates
        // against the session view + this immutable player profile — never
        // the live world or entity.
        val initialStart = currentStablePlannerNode(view) ?: player.blockPos.toFastVec()
        val discovery = if (config.allowJump && config.allowManeuverDiscovery) {
            ManeuverDiscovery(PlayerPhysicsProfile.capture(player), view, preferredOrigin = initialStart)
        } else {
            null
        }
        // Anytime two-phase search: the initial search runs template-only
        // (cheap, publishes a walkable path fast); discovery joins once a
        // first plan is out — or once templates provably/probably cannot
        // connect — and from then on proposes at expansion time as before.
        // The improvement pass then upgrades the adopted route with the
        // expensive maneuver edges, behind the plan-swap hysteresis.
        val discoveryGate = AtomicBoolean(false)
        // A connected template path uses explicit, route-local anytime
        // discovery. Opening a jump fan on every later D* predecessor
        // expansion floods repairs with unrelated optimistic edges.
        val discoverOnExpansion = AtomicBoolean(false)

        // Snapshot the start→goal corridor before the worker starts: section
        // copies are client-thread-only, and each worker fault costs a frame
        // of planning latency.
        view.prefetch(
            initialStart.x, initialStart.y, initialStart.z,
            goal.targetNode.x, goal.targetNode.y, goal.targetNode.z,
        )

        val graph = LazyGraph<FastVector>(
            successorProvider = { node ->
                var edges = moves.successors(view, node)
                if (discoveryGate.get()) {
                    discovery?.successorsFrom(node)?.takeIf { it.isNotEmpty() }?.let { edges = edges + it }
                }
                penalized(node, edges)
            },
            // True inverse enumeration — never default to the symmetric
            // assumption: it mirrors legal step-downs into illegal step-ups
            // (e.g. under low ceilings) with the wrong cost attached.
            predecessorProvider = { node ->
                var edges = moves.predecessors(view, node)
                if (discoveryGate.get()) {
                    discovery?.let {
                        val maneuvers = if (discoverOnExpansion.get()) {
                            it.predecessorsInto(node)
                        } else {
                            it.knownPredecessorsInto(node)
                        }
                        if (maneuvers.isNotEmpty()) edges = edges + maneuvers
                    }
                }
                if (edgePenalties.isEmpty() && approachRejectedEdges.isEmpty()) edges
                else buildMap(edges.size) {
                    for ((from, cost) in edges) {
                        val edge = EdgeKey(from, node)
                        if (edge !in approachRejectedEdges) {
                            put(from, cost * (edgePenalties[edge] ?: 1.0))
                        }
                    }
                }
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
            start = initialStart,
            goal = goal.targetNode,
            heuristic = { a, b -> movementHeuristic(moves.caps, a, b) },
            // Stable tie-break between equal-cost successors so the coarse path
            // doesn't flip between runs and force a re-refinement / shortcut
            // angle change. FastVector is a Long; natural ordering is enough.
            nodeTieBreaker = naturalOrder(),
        )

        // The refinement config is captured per session: the worker must not
        // read the live automation-config object.
        activeSession = ActiveSession(
            handle, graph, planner, edgePenalties, approachRejectedEdges, view, moves, discovery,
            discoveryGate, discoverOnExpansion, refinementConfig,
        )
        PlannerMetrics.sink.planStart(handle.id, planner.start, goal.targetNode)
        enqueue(activeSession ?: return handle) { computeActivePath(ComputeCause.Initial) }
        return handle
    }

    fun AutomatedSafeContext.refreshActiveTraversal(): TraversalHandle? {
        val session = activeSession ?: return null
        if (session.handle.status.isTerminal) return session.handle
        // Lost recovery can end with the player's footprint supported by a
        // neighbouring block while player.blockPos itself is over the hole.
        // Anchor to that actually-overlapped stance instead of repeatedly
        // "replanning" from no start node and leaving the executor Lost.
        val stableNode = currentStablePlannerNode(allowFootprintRecovery = true) ?: return session.handle
        enqueue(session) {
            session.planner.updateStart(stableNode)
            computeActivePath(ComputeCause.ExplicitRefresh)
        }
        return session.handle
    }

    fun AutomatedSafeContext.refreshActiveTraversalIfStartMoved(): TraversalHandle? {
        val session = activeSession ?: return null
        if (session.handle.status.isTerminal) return session.handle

        val playerBlock = currentStablePlannerNode() ?: return session.handle
        queueStartRefresh(session, playerBlock)
        return session.handle
    }

    /** Worker-confined start advance, lazy validation, and budget continuation. */
    private fun ActiveSession.refreshFromStart(playerBlock: FastVector) {
        if (!isCurrent(this) || handle.status.isTerminal) return

        if (chunkTopologyDirty.getAndSet(false)) {
            // Correctness-first fallback for chunk visibility changes. A
            // section can switch wholesale between unknown and observed, so
            // until WP5 supplies section-scoped connectivity/invalidation we
            // rebuild the lazy graph once for the whole event batch.
            //
            // Re-anchor to the adopted route, not the raw player column: a
            // mid-shortcut rebuild anchored off-lattice regenerates a
            // geometrically different refined path and snaps the executor's
            // steering angle — the churn source on long outdoor traversals,
            // where chunks load ahead of the path every few seconds.
            val anchor = lastRefinedPath?.let { refined ->
                refinedRouteAnchorIndex(refined, playerBlock)?.let { refined[it] }
            } ?: playerBlock
            discovery?.clearWorldCache()
            planner.initialize(clearGraph = true)
            lastCoarsePath = null
            lastRefinedPath = null
            lastEdgeAnnotations = emptyMap()
            planner.updateStart(anchor)
            computeActivePath(ComputeCause.ChunkTopology)
            return
        }

        if (playerBlock == handle.goal.targetNode) {
            handle.succeed()
            return
        }

        if (playerBlock == planner.start) {
            // A budgeted compute can time out before producing even a partial
            // path. The player then cannot move, so waiting for a start-node
            // change deadlocks the traversal in Partial forever. Continue one
            // budget slice per player tick until D* Lite becomes consistent.
            if (handle.status == TraversalHandle.Status.Partial) {
                computeActivePath(ComputeCause.BudgetContinuation)
            }
            return
        }

        val refined = lastRefinedPath
        if (refined.isNullOrEmpty()) {
            planner.updateStart(playerBlock)
            computeActivePath(ComputeCause.StartAdvanceNoRefinedPath)
            return
        }

        // Anchor the start by PROJECTION onto the refined route, never to the
        // raw player column: while walking an any-angle shortcut the player's
        // block is usually on neither the refined nor the coarse node list,
        // and an exact-match-only advance left the start pinned at the
        // shortcut head for its whole length. Projection advances the start
        // segment-by-segment along the adopted route; a player genuinely off
        // the route keeps the cached plan and is owned by Lost recovery.
        val plannerIndex = refined.indexOf(planner.start)
        val anchorIndex = refinedRouteAnchorIndex(refined, playerBlock) ?: return
        if (anchorIndex <= plannerIndex) {
            // Partial is a search state, not an execution state: it still
            // needs one continuation slice per tick even while the player is
            // inside a long refined shortcut and therefore remains anchored
            // to that segment's head. Restricting continuation to
            // playerBlock == planner.start deadlocked dynamic repair after an
            // invalidated shortcut was republished as a partial segment.
            if (handle.status == TraversalHandle.Status.Partial) {
                computeActivePath(ComputeCause.BudgetContinuation)
            }
            return
        }

        planner.updateStart(refined[anchorIndex])
        computeActivePath(ComputeCause.StartAdvance)
    }

    fun cancelActiveTraversal(): Boolean {
        val session = activeSession ?: return false
        session.handle.cancel()
        activeSession = null
        return true
    }

    private fun enqueue(session: ActiveSession, block: ActiveSession.() -> Unit) {
        plannerWorker.execute {
            if (!isCurrent(session)) return@execute
            try {
                session.block()
            } catch (t: Throwable) {
                LOG.error("[Pathfinder] Planner worker failed for traversal ${session.handle.id}", t)
                session.handle.failureReason = "Planner worker failed: ${t.message ?: t::class.simpleName}"
                session.handle.status = TraversalHandle.Status.Failed
            }
        }
    }

    private fun isCurrent(session: ActiveSession): Boolean =
        activeSession === session && !session.handle.status.isTerminal

    /** Coalesces client ticks while a 50 ms worker slice is still running. */
    private fun queueStartRefresh(session: ActiveSession, start: FastVector) {
        session.pendingStart.set(start)
        if (!session.startRefreshQueued.compareAndSet(false, true)) return
        enqueue(session) {
            try {
                while (isCurrent(this)) {
                    val latest = pendingStart.getAndSet(null) ?: break
                    refreshFromStart(latest)
                }
            } finally {
                startRefreshQueued.set(false)
                val pending = pendingStart.get()
                if (pending != null && isCurrent(this)) queueStartRefresh(this, pending)
            }
        }
    }

    fun AutomatedSafeContext.synchronizeWorldChange(pos: BlockPos): TraversalHandle? {
        val session = activeSession ?: return null
        if (session.handle.status.isTerminal) return session.handle

        // Batch: a /fill or explosion delivers dozens of block events in one
        // client tick. One worker pass over the union costs one graph sync
        // and ONE republication — per-event passes republished a new path
        // identity per block, resetting executor segment state every time.
        session.pendingBlockChanges.add(pos)
        queueBlockSync(session)
        return session.handle
    }

    /** Coalesces block-update events while a worker sync is still running. */
    private fun queueBlockSync(session: ActiveSession) {
        if (!session.blockSyncQueued.compareAndSet(false, true)) return
        enqueue(session) {
            try {
                while (isCurrent(this)) {
                    val batch = generateSequence { pendingBlockChanges.poll() }.toSet()
                    if (batch.isEmpty()) break
                    synchronizeWorldChangesOnWorker(batch)
                }
            } finally {
                blockSyncQueued.set(false)
                if (pendingBlockChanges.isNotEmpty() && isCurrent(this)) queueBlockSync(this)
            }
        }
    }

    private fun ActiveSession.synchronizeWorldChangesOnWorker(batch: Set<BlockPos>) {
        if (!isCurrent(this) || handle.status.isTerminal) return

        val affectedNodes = HashSet<FastVector>()
        for (pos in batch) {
            affectedNodes += moves.affectedNodes(pos.x, pos.y, pos.z)
            // Discovered maneuver edges read a larger region than templates:
            // drop and re-open any whose flight volume could see this change,
            // and resync their endpoints through the same pass.
            discovery?.invalidateAround(pos.x, pos.y, pos.z)?.let { affectedNodes += it }
        }
        val syncStartNanos = System.nanoTime()
        val sync = planner.synchronizeAffected(affectedNodes)
        PlannerMetrics.sink.syncEnd(
            traversalId = handle.id,
            nodesChecked = sync.nodesChecked,
            edgesAdded = sync.edgesAdded,
            edgesRemoved = sync.edgesRemoved,
            edgesChanged = sync.edgesChanged,
            wallMicros = (System.nanoTime() - syncStartNanos) / 1_000,
        )
        handle.lastSynchronization = TraversalHandle.SynchronizationStats(
            nodesChecked = sync.nodesChecked,
            edgesAdded = sync.edgesAdded,
            edgesRemoved = sync.edgesRemoved,
            edgesChanged = sync.edgesChanged,
        )

        // Only a change that can touch the adopted route invalidates the
        // published plan. Off-route changes still repair the search tree,
        // but the executing plan (and therefore the executor's segment state
        // and steering angle) survives them — replacing it on EVERY remote
        // edge change was the main source of mid-shortcut trajectory snaps.
        // The search start is deliberately NOT re-anchored to the player
        // here: start advancement is owned by the route-projection logic in
        // refreshFromStart, and Lost recovery owns genuine displacement.
        if (sync.edgesAdded > 0 || sync.edgesRemoved > 0 || sync.edgesChanged > 0) {
            if (adoptedRouteAffected(affectedNodes, batch)) {
                lastCoarsePath = null
                lastRefinedPath = null
            }
        }

        computeActivePath(ComputeCause.WorldChange)
    }

    /**
     * True when a world-change batch can invalidate the adopted plan: an
     * affected node lies on the route, or a changed block sits inside a
     * refined segment's swept corridor (shortcuts cut corners, so corridor
     * blocks are not necessarily near any route node). Conservative when no
     * plan is adopted.
     */
    private fun ActiveSession.adoptedRouteAffected(
        affectedNodes: Set<FastVector>,
        changedBlocks: Set<BlockPos> = emptySet(),
    ): Boolean {
        val coarse = lastCoarsePath ?: return true
        val refined = lastRefinedPath ?: return true
        if (coarse.isEmpty() || refined.isEmpty()) return true
        val routeNodes = HashSet<FastVector>(coarse.size + refined.size).apply {
            addAll(coarse)
            addAll(refined)
        }
        if (affectedNodes.any(routeNodes::contains)) return true
        return changedBlocks.any { pos ->
            refined.zipWithNext().any { (a, b) -> posNearSegment(pos, a, b) }
        }
    }

    private fun posNearSegment(pos: BlockPos, a: FastVector, b: FastVector): Boolean {
        if (pos.y < minOf(a.y, b.y) - 1 || pos.y > maxOf(a.y, b.y) + 2) return false
        val px = pos.x + 0.5
        val pz = pos.z + 0.5
        val ax = a.x + 0.5
        val az = a.z + 0.5
        val dx = (b.x - a.x).toDouble()
        val dz = (b.z - a.z).toDouble()
        val lengthSq = dx * dx + dz * dz
        if (lengthSq < 1.0E-9) {
            return kotlin.math.abs(px - ax) <= ROUTE_CORRIDOR_MARGIN &&
                kotlin.math.abs(pz - az) <= ROUTE_CORRIDOR_MARGIN
        }
        val t = (((px - ax) * dx + (pz - az) * dz) / lengthSq).coerceIn(0.0, 1.0)
        return kotlin.math.abs(px - (ax + dx * t)) <= ROUTE_CORRIDOR_MARGIN &&
            kotlin.math.abs(pz - (az + dz * t)) <= ROUTE_CORRIDOR_MARGIN
    }

    fun completeActiveTraversal(): Boolean {
        val session = activeSession ?: return false
        session.handle.succeed()
        return true
    }

    fun debugInfo(): String = activeSession?.handle?.debugString() ?: "No active traversal"

    fun lazyGraphSnapshot(maxNodes: Int, maxEdges: Int): LazyGraphSnapshot {
        val session = activeSession ?: return LazyGraphSnapshot()
        session.requestedDebugNodes.set(maxNodes.coerceAtLeast(0))
        session.requestedDebugEdges.set(maxEdges.coerceAtLeast(0))
        return session.debugSnapshot.trimTo(maxNodes, maxEdges)
    }

    /** Called only on the planner worker. */
    private fun ActiveSession.publishDebugSnapshot() {
        val maxNodes = requestedDebugNodes.get().coerceAtLeast(0)
        val maxEdges = requestedDebugEdges.get().coerceAtLeast(0)
        if (maxNodes == 0 && maxEdges == 0) return
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

        debugSnapshot = LazyGraphSnapshot(
            totalNodes = graph.size,
            nodes = nodes,
            edges = edges,
            truncated = graph.size > nodes.size || edgeLimitReached,
        )
    }

    /** One budgeted D* Lite slice with its metrics: (result, coarse path). */
    private fun ActiveSession.runComputeSlice(cause: ComputeCause): Pair<DStarLite.ComputeResult, List<FastVector>> {
        PlannerMetrics.sink.computeStart(handle.id, cause.metricName, planner.start, graph.size)
        val computeStartNanos = System.nanoTime()
        val result = planner.computeShortestPath(handle.config.computeBudget.toDuration(DurationUnit.MILLISECONDS))
        val computeWallMicros = (System.nanoTime() - computeStartNanos) / 1_000
        val coarsePath = planner.path(handle.config.maxPathLength)
        val reachedGoal = coarsePath.lastOrNull() == handle.goal.targetNode
        if (computeCount++ == 0) {
            PlannerMetrics.sink.initialPath(handle.id, result.processedNodes, computeWallMicros, result.timedOut, coarsePath.size, reachedGoal)
        } else {
            PlannerMetrics.sink.repairEnd(handle.id, result.processedNodes, computeWallMicros, result.timedOut, coarsePath.size, reachedGoal)
        }
        return result to coarsePath
    }

    private fun ActiveSession.applyValidationSync(affected: Set<FastVector>) {
        val syncStartNanos = System.nanoTime()
        val sync = planner.synchronizeAffected(affected)
        PlannerMetrics.sink.syncEnd(
            traversalId = handle.id,
            nodesChecked = sync.nodesChecked,
            edgesAdded = sync.edgesAdded,
            edgesRemoved = sync.edgesRemoved,
            edgesChanged = sync.edgesChanged,
            wallMicros = (System.nanoTime() - syncStartNanos) / 1_000,
        )
        handle.lastSynchronization = TraversalHandle.SynchronizationStats(
            nodesChecked = sync.nodesChecked,
            edgesAdded = sync.edgesAdded,
            edgesRemoved = sync.edgesRemoved,
            edgesChanged = sync.edgesChanged,
        )
        // Validation corrects CANDIDATE-path maneuver costs; the adopted
        // plan's own edges were validated when it was adopted, so it only
        // needs to go when the corrected landings actually lie on it.
        if (adoptedRouteAffected(affected)) {
            lastCoarsePath = null
            lastRefinedPath = null
        }
    }

    private fun ActiveSession.computeActivePath(cause: ComputeCause) {
        if (!isCurrent(this)) return

        // Keep executing the last immutable path while a repair runs. Only an
        // initial/no-path compute exposes Planning to the client executor.
        if (handle.path.size < 2) handle.status = TraversalHandle.Status.Planning
        var (result, coarsePath) = runComputeSlice(cause)
        if (!isCurrent(this)) return
        var processedNodes = result.processedNodes

        // Lazy edge evaluation (LIS, plan §5): discovery proposes maneuver
        // edges with lower-bound costs during graph expansion; the physics
        // sims run here, exclusively for edges the search selected onto the
        // candidate path. Each correction/removal is an ordinary D* edge
        // update followed by a repair. The loop runs until the selected path
        // carries no optimistic edge — with lower-bound optimism that path
        // is exactly the one eager validation would have chosen, and nothing
        // is published before then, so the executor can never launch an
        // unvalidated maneuver. Bounded rounds per worker task keep queued
        // world updates from starving behind a pathological alternation.
        if (discovery != null) {
            var rounds = 0
            while (true) {
                if (!isCurrent(this)) return
                // Partial paths get horizon-limited validation: only edges
                // the executor could imminently reach are resolved, since
                // the frontier tail changes with every repair slice anyway.
                // Once the path claims the goal, validation is exhaustive —
                // a published Ready path carries no optimistic edge and is
                // exactly the eager planner's choice.
                val horizon = if (coarsePath.lastOrNull() == handle.goal.targetNode) {
                    null
                } else {
                    PARTIAL_VALIDATION_HORIZON
                }
                val affected = discovery.validatePathEdges(coarsePath, horizon)
                if (affected.isEmpty()) break
                applyValidationSync(affected)
                if (++rounds >= MAX_LAZY_VALIDATION_ROUNDS) {
                    handle.graphSize = graph.size
                    handle.processedNodes += processedNodes
                    if (handle.path.size < 2) handle.status = TraversalHandle.Status.Partial
                    publishDebugSnapshot()
                    enqueue(this) { computeActivePath(ComputeCause.LazyValidation) }
                    return
                }
                val (repairResult, repairedPath) = runComputeSlice(ComputeCause.LazyValidation)
                processedNodes += repairResult.processedNodes
                result = repairResult
                coarsePath = repairedPath
            }
        }

        // Position-only graph nodes do not encode arrival heading. A
        // rising gap jump can therefore look legal even when the selected
        // route reaches its takeoff through a 90° turn — the production
        // turn-then-rise deadlock. Remove that edge unless this route
        // supplies an aligned incoming runway.
        invalidMomentumApproach(coarsePath)?.let { (from, to) ->
            approachRejectedEdges += EdgeKey(from, to)
            planner.updateEdge(from, to, Double.POSITIVE_INFINITY)
            lastCoarsePath = null
            lastRefinedPath = null
            lastEdgeAnnotations = emptyMap()
            handle.graphSize = graph.size
            handle.processedNodes += processedNodes
            enqueue(this) { computeActivePath(ComputeCause.ApproachValidation) }
            return
        }

        // A timed-out slice that hasn't connected the goal yet leaves the
        // search inconsistent, and a path extracted from that state is NOT
        // a prefix of the eventual route (observed: a warm-JIT first slice
        // published a 10-node sideways walk the executor departed on, then
        // waited 50 ticks at its end). Keep the previous published plan —
        // the executor continues it — report Partial so the per-tick budget
        // continuation resumes the search, and publish only consistent
        // results.
        if (result.timedOut && coarsePath.lastOrNull() != handle.goal.targetNode) {
            handle.graphSize = graph.size
            handle.processedNodes += processedNodes
            handle.status = TraversalHandle.Status.Partial
            publishDebugSnapshot()
            // Template-phase cap: in an unbounded world a goal separated by
            // a jump-only link never exhausts (the backward component is
            // infinite), so a phase-1 search that keeps timing out without
            // connecting brings discovery in after a few full slices instead
            // of walking the frontier forever.
            if (discovery != null && !discoveryGate.get() &&
                ++templateSlices >= TEMPLATE_PHASE_MAX_SLICES
            ) {
                activateDiscoveryOverKnownGraph()
                enqueue(this) { computeActivePath(ComputeCause.DiscoveryPhase) }
            }
            return
        }

        // Template-only search concluded without connecting the goal (the
        // reachable component is exhausted, or extraction dead-ends): the
        // missing links are exactly what discovery provides. Retrofit its
        // proposals onto the explored graph and continue the same search —
        // a traversal is never failed out of phase 1.
        if (coarsePath.lastOrNull() != handle.goal.targetNode &&
            discovery != null && !discoveryGate.get()
        ) {
            activateDiscoveryOverKnownGraph()
            handle.graphSize = graph.size
            handle.processedNodes += processedNodes
            if (handle.path.size < 2) handle.status = TraversalHandle.Status.Partial
            publishDebugSnapshot()
            enqueue(this) { computeActivePath(ComputeCause.DiscoveryPhase) }
            return
        }

        // If the new coarse path is just a forward-advance of the prior coarse
        // path (player walked along it without any topology change), reuse the
        // previously refined path by reference. This avoids both the cost of
        // re-refining and the downstream executor's rebuild-on-reference-change
        // which would otherwise reset segment progress and cause oscillation.
        val priorCoarse = lastCoarsePath
        val priorRefined = lastRefinedPath
        // Don't reuse the cache once the coarse path has collapsed to just the
        // goal node — the executor stops on path.size <= 1 and would never see
        // AtGoal if we kept the old long refined path.
        val structurallyUnchanged = priorCoarse != null
            && priorRefined != null
            && coarsePath.size >= 2
            && isForwardAdvance(priorCoarse, coarsePath)

        if (structurallyUnchanged) {
            handle.publishPlan(coarsePath, priorRefined, lastEdgeAnnotations)
            handle.graphSize = graph.size
            handle.processedNodes += processedNodes
            handle.failureReason = null
            handle.status = pathStatus(result.timedOut, coarsePath, handle)
            publishDebugSnapshot()
            if (handle.status == TraversalHandle.Status.Ready) scheduleImprovement(this)
            return
        }

        // Plan-swap hysteresis: a still-valid adopted plan is only replaced
        // when the planner's new optimum is decisively cheaper. Without
        // this, every marginal improvement and every off-suffix extraction
        // re-refines a slightly different geometry, and each republication
        // snaps the executor's steering angle — the observed fast
        // trajectory changes while walking the refined path.
        if (priorCoarse != null && priorRefined != null &&
            priorCoarse.lastOrNull() == handle.goal.targetNode
        ) {
            val adoptedCost = adoptedRemainingCost(priorCoarse)
            if (adoptedCost != null) {
                val candidateCost = if (coarsePath.lastOrNull() == handle.goal.targetNode) {
                    pathCost(coarsePath)
                } else {
                    null
                }
                val keepAdopted = candidateCost == null ||
                    candidateCost > adoptedCost * (1.0 - PLAN_SWAP_IMPROVEMENT)
                if (keepAdopted) {
                    handle.publishPlan(priorCoarse, priorRefined, lastEdgeAnnotations)
                    handle.graphSize = graph.size
                    handle.processedNodes += processedNodes
                    handle.failureReason = null
                    handle.status = pathStatus(result.timedOut, priorCoarse, handle)
                    publishDebugSnapshot()
                    if (handle.status == TraversalHandle.Status.Ready) scheduleImprovement(this)
                    return
                }
            }
        }

        val refinement = PathRefiner.refine(view, coarsePath, refinementConfig)
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
        lastCoarsePath = coarsePath
        lastRefinedPath = refinement.path
        lastEdgeAnnotations = discovery?.annotationsFor(refinement.path).orEmpty()
        improvementCursor = 0

        handle.publishPlan(coarsePath, refinement.path, lastEdgeAnnotations)
        handle.lastRefinement = refinement.stats
        handle.lastRefinementDebug = refinement.debug
        handle.graphSize = graph.size
        handle.processedNodes += processedNodes
        handle.failureReason = null
        logRefinementSummaryIfChanged(this)

        handle.status = pathStatus(result.timedOut, coarsePath, handle)
        if (handle.status == TraversalHandle.Status.Ready && coarsePath.size <= 1 && coarsePath.lastOrNull() == handle.goal.targetNode) {
            handle.succeed()
        }
        publishDebugSnapshot()

        // First goal-connected plan out: switch to the improvement phase.
        // Discovery joins expansion from here on, and the pass below starts
        // proposing maneuver shortcuts along the freshly adopted route.
        if (handle.status == TraversalHandle.Status.Ready && discovery != null) {
            discoveryGate.compareAndSet(false, true)
            scheduleImprovement(this)
        }
    }

    /**
     * Phase flip for a search whose templates could not connect the goal:
     * enable maneuver proposals and retrofit them onto the already-explored
     * graph. Discovery is landing-anchored, so proposing INTO every known
     * node covers exactly the edges the gated providers would have added
     * had discovery been active during expansion (T2 discovery-monotone:
     * edges only appear, through the ordinary update machinery).
     */
    private fun ActiveSession.activateDiscoveryOverKnownGraph() {
        if (discovery == null) return
        if (!discoveryGate.compareAndSet(false, true)) return
        // Template search failed, so maneuver discovery is now part of the
        // connectivity substrate and must follow future graph expansion.
        discoverOnExpansion.set(true)
        improvementCursor = 0
        val known = graph.nodes.toList()
        var gained = 0
        known.forEach { landing -> if (injectDiscoveredEdges(landing)) gained++ }
        LOG.info(
            "[Pathfinder] Discovery joined traversal ${handle.id} after template phase: " +
                "${known.size} known landings scanned, $gained gained edges"
        )
    }

    /**
     * Proposes discovery edges into [landing] and declares any new or
     * cheaper ones to the search. Proposals are memoized per landing and
     * declaration skips known-equal edges, so re-scanning a route is cheap
     * and idempotent. Returns true if the graph changed.
     */
    private fun ActiveSession.injectDiscoveredEdges(landing: FastVector): Boolean {
        val discovery = discovery ?: return false
        val proposals = discovery.predecessorsInto(landing)
        if (proposals.isEmpty()) return false
        var mutated = false
        proposals.forEach { (takeoff, cost) ->
            if (EdgeKey(takeoff, landing) in approachRejectedEdges) return@forEach
            val penalizedCost = cost * (edgePenalties[EdgeKey(takeoff, landing)] ?: 1.0)
            val known = graph.knownSuccessors(takeoff)[landing]
            if (known == null || known > penalizedCost + 1.0E-9) {
                planner.updateEdge(takeoff, landing, penalizedCost)
                mutated = true
            }
        }
        return mutated
    }

    /**
     * Anytime improvement (research plan §8.1 LIS discipline, user-visible
     * as "find a cheap path fast, then upgrade it"): walk the adopted route
     * and propose discovered jump edges into each of its nodes — the
     * expensive maneuver shortcuts the template path cannot see. Budgeted
     * per worker slice so world syncs interleave; adoption of anything
     * found goes through the same plan-swap hysteresis as every replan, so
     * marginal gains never churn the executing path.
     */
    private fun ActiveSession.improveAdoptedPath() {
        if (!isCurrent(this) || handle.status.isTerminal) return
        if (discovery == null || !discoveryGate.get()) return
        val route = lastCoarsePath ?: return
        var index = improvementCursor
        var mutated = false
        var examined = 0
        while (index < route.size && examined < IMPROVEMENT_LANDINGS_PER_SLICE) {
            if (injectDiscoveredEdges(route[index])) mutated = true
            examined++
            index++
        }
        improvementCursor = index
        if (mutated) computeActivePath(ComputeCause.Improvement)
        if ((lastCoarsePath?.size ?: 0) > improvementCursor) scheduleImprovement(this)
    }

    /** Coalesces improvement passes; safe to call from any publish site. */
    private fun scheduleImprovement(session: ActiveSession) {
        if (session.discovery == null || !session.discoveryGate.get()) return
        if ((session.lastCoarsePath?.size ?: 0) <= session.improvementCursor) return
        if (!session.improvementQueued.compareAndSet(false, true)) return
        enqueue(session) {
            improvementQueued.set(false)
            improveAdoptedPath()
        }
    }

    /**
     * Live-graph cost of the adopted coarse plan from the current search
     * start — null when the start is no longer on the plan or an edge died,
     * both of which mean the plan must be replaced, not defended.
     */
    private fun ActiveSession.adoptedRemainingCost(adopted: List<FastVector>): Double? {
        val from = adopted.indexOf(planner.start)
        if (from < 0) return null
        return pathCost(adopted.subList(from, adopted.size))
    }

    /** First long rising edge whose selected predecessor does not form an aligned runway. */
    private fun ActiveSession.invalidMomentumApproach(path: List<FastVector>): Pair<FastVector, FastVector>? {
        if (path.size < 2) return null
        for (index in 0 until path.lastIndex) {
            val from = path[index]
            val to = path[index + 1]
            val dx = (to.x - from.x).toDouble()
            val dz = (to.z - from.z).toDouble()
            val horizontal = kotlin.math.hypot(dx, dz)
            if (horizontal < 1.0E-6) continue
            // Scope the position-only fallback to the known broken class:
            // two-forward-or-longer rising jumps. Flat discovered jumps have
            // live robust certificates and globally removing them based on
            // one route's heading can strand a later, valid approach.
            val risingGap = to.y > from.y && horizontal >= MOMENTUM_EDGE_MIN_LENGTH
            if (!risingGap) continue

            // The coarse predecessor is only a position-graph tie-break; on
            // a real platform the executor may align along a different pair
            // of stance blocks before launching. Accept that route when the
            // world itself supplies two aligned runway blocks. The original
            // turn-then-rise failure still rejects because its isolated
            // takeoff has no blocks behind it in the jump direction.
            if (hasPhysicalMomentumRunway(from, dx, dz, horizontal)) continue

            val predecessor = when {
                index > 0 -> path[index - 1]
                else -> lastCoarsePath?.let { prior ->
                    prior.indexOf(from).takeIf { it > 0 }?.let { prior[it - 1] }
                }
            } ?: return from to to
            val inX = (from.x - predecessor.x).toDouble()
            val inZ = (from.z - predecessor.z).toDouble()
            val incomingLength = kotlin.math.hypot(inX, inZ)
            if (incomingLength < MOMENTUM_RUNWAY_MIN_LENGTH) return from to to
            val cosine = (inX * dx + inZ * dz) / (incomingLength * horizontal)
            if (cosine < MOMENTUM_RUNWAY_MIN_COSINE) return from to to
        }
        return null
    }

    private fun ActiveSession.hasPhysicalMomentumRunway(
        takeoff: FastVector,
        dx: Double,
        dz: Double,
        horizontal: Double,
    ): Boolean {
        for (back in 1..2) {
            val x = takeoff.x - kotlin.math.round(dx / horizontal * back).toInt()
            val z = takeoff.z - kotlin.math.round(dz / horizontal * back).toInt()
            if (!MoveTable.isStance(view, x, takeoff.y, z)) return false
        }
        return true
    }

    /** Sum of known graph costs along [path]; null on any missing edge. */
    private fun ActiveSession.pathCost(path: List<FastVector>): Double? {
        var total = 0.0
        for (i in 0 until path.lastIndex) {
            val cost = graph.knownSuccessors(path[i])[path[i + 1]] ?: return null
            if (!cost.isFinite()) return null
            total += cost
        }
        return total
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
        allowFootprintRecovery: Boolean = false,
    ): FastVector? {
        if (!player.isOnGround) return null
        view ?: return null
        val blockPos = player.blockPos
        if (MoveTable.isStance(view, blockPos.x, blockPos.y, blockPos.z)) return blockPos.toFastVec()
        if (!allowFootprintRecovery) return null
        return footprintSupportedStance(view, player.pos, blockPos)
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
        val edgePenalties: HashMap<EdgeKey, Double>,
        val approachRejectedEdges: HashSet<EdgeKey>,
        val view: SnapshotWorldView,
        val moves: MoveTable.MoveSet,
        val discovery: ManeuverDiscovery?,
        /** False during the template-only phase; latched true forever after. */
        val discoveryGate: AtomicBoolean,
        /** True only when discovery is required to establish connectivity. */
        val discoverOnExpansion: AtomicBoolean,
        /** Captured at request time; the worker never reads live config objects. */
        val refinementConfig: PathRefinementConfig,
        var lastRefinementLogKey: String? = null,
        var lastCoarsePath: List<FastVector>? = null,
        var lastRefinedPath: List<FastVector>? = null,
        var lastEdgeAnnotations: Map<Pair<FastVector, FastVector>, TraversalHandle.EdgeAnnotation> = emptyMap(),
        var computeCount: Int = 0,
        /** Next adopted-route index the improvement pass will scan (worker-only). */
        var improvementCursor: Int = 0,
        /** Timed-out template-phase slices, for the phase-1 cap (worker-only). */
        var templateSlices: Int = 0,
        val improvementQueued: AtomicBoolean = AtomicBoolean(false),
        val chunkTopologyDirty: AtomicBoolean = AtomicBoolean(false),
        val pendingStart: AtomicReference<FastVector?> = AtomicReference(null),
        val startRefreshQueued: AtomicBoolean = AtomicBoolean(false),
        /** Immutable positions only — snapshotted at the event listener. */
        val pendingBlockChanges: java.util.concurrent.ConcurrentLinkedQueue<BlockPos> =
            java.util.concurrent.ConcurrentLinkedQueue(),
        val blockSyncQueued: AtomicBoolean = AtomicBoolean(false),
        val requestedDebugNodes: AtomicInteger = AtomicInteger(0),
        val requestedDebugEdges: AtomicInteger = AtomicInteger(0),
        @Volatile var debugSnapshot: LazyGraphSnapshot = LazyGraphSnapshot(),
    )

    /**
     * Chunk traffic at the search fringe must not stop an already executable
     * route. A transition is immediately relevant only when it intersects the
     * current refined route, or (before any route exists) the forward corridor
     * between start and goal. Other evicted snapshots will be copied fresh if
     * a later search actually reaches them.
     */
    private fun ActiveSession.chunkTransitionAffectsActiveSearch(chunk: net.minecraft.util.math.ChunkPos): Boolean {
        val path = handle.path
        if (path.isNotEmpty()) {
            if (path.any { (it.x shr 4) == chunk.x && (it.z shr 4) == chunk.z }) return true
            val minX = chunk.startX
            val maxX = chunk.endX
            val minZ = chunk.startZ
            val maxZ = chunk.endZ
            return path.zipWithNext().any { (a, b) ->
                maxOf(a.x, b.x) >= minX && minOf(a.x, b.x) <= maxX &&
                    maxOf(a.z, b.z) >= minZ && minOf(a.z, b.z) <= maxZ
            }
        }

        val start = handle.coarsePath.firstOrNull() ?: handle.path.firstOrNull() ?: return true
        val goal = handle.goal.targetNode
        val sx = start.x shr 4
        val sz = start.z shr 4
        val gx = goal.x shr 4
        val gz = goal.z shr 4
        return if (kotlin.math.abs(gx - sx) >= kotlin.math.abs(gz - sz)) {
            chunk.x in minOf(sx, gx)..maxOf(sx, gx) &&
                chunk.z in (minOf(sz, gz) - 1)..(maxOf(sz, gz) + 1)
        } else {
            chunk.z in minOf(sz, gz)..maxOf(sz, gz) &&
                chunk.x in (minOf(sx, gx) - 1)..(maxOf(sx, gx) + 1)
        }
    }

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

        val stableNode = currentStablePlannerNode()
        enqueue(session) { reportEdgeObstructedOnWorker(from, to, stableNode) }
        return true
    }

    private fun ActiveSession.reportEdgeObstructedOnWorker(
        from: FastVector,
        to: FastVector,
        stableNode: FastVector?,
    ) {
        if (!isCurrent(this) || handle.status.isTerminal) return

        val key = EdgeKey(from, to)
        val factor = ((edgePenalties[key] ?: 1.0) * OBSTRUCTION_PENALTY_FACTOR)
            .coerceAtMost(MAX_OBSTRUCTION_PENALTY)
        edgePenalties[key] = factor
        PlannerMetrics.sink.edgeObstructed(handle.id, from, to, factor)
        LOG.info("[Pathfinder] Edge obstructed ${from.toBlockPos().toShortString()} -> ${to.toBlockPos().toShortString()}, penalty x${"%.0f".format(factor)}")

        lastCoarsePath = null
        lastRefinedPath = null
        planner.synchronizeAffected(setOf(from, to))
        stableNode?.let { planner.updateStart(it) }
        computeActivePath(ComputeCause.EdgeObstructed)
    }

    private const val OBSTRUCTION_PENALTY_FACTOR = 8.0
    private const val MAX_OBSTRUCTION_PENALTY = 4096.0

    // Lazy-validation rounds per worker task before yielding to queued
    // world updates. Each round validates every optimistic edge on the
    // candidate path and repairs, so alternation this deep is pathological.
    private const val MAX_LAZY_VALIDATION_ROUNDS = 16

    // How far ahead of the path start maneuvers are physics-validated while
    // the path is still partial (executor-imminent edges only). Matches a
    // couple seconds of travel; the executor's live launch gate re-checks
    // every jump at execution time regardless.
    private const val PARTIAL_VALIDATION_HORIZON = 24.0

    // Plan-swap hysteresis: the executing plan is only replaced when the new
    // optimum is at least this fraction cheaper (or the old plan died).
    // Below it, geometry stability is worth more than the saved ticks.
    private const val PLAN_SWAP_IMPROVEMENT = 0.05

    // Timed-out template-only budget slices before discovery joins a search
    // that has not connected the goal yet (phase-1 latency cap).
    private const val TEMPLATE_PHASE_MAX_SLICES = 4

    // Adopted-route landings scanned per improvement slice, so world syncs
    // and start advances interleave with the maneuver-proposal scan.
    private const val IMPROVEMENT_LANDINGS_PER_SLICE = 48

    // Changed blocks within this lateral distance of a refined segment
    // (and -1..+2 of its walking level) invalidate the adopted plan.
    private const val ROUTE_CORRIDOR_MARGIN = 1.5

    // Position-only planner fallback for momentum edges: require at least
    // one aligned incoming block and reject turns sharper than 15 degrees.
    private const val MOMENTUM_EDGE_MIN_LENGTH = 1.9
    private const val MOMENTUM_RUNWAY_MIN_LENGTH = 0.9
    private const val MOMENTUM_RUNWAY_MIN_COSINE = 0.965925826

    private enum class ComputeCause(val metricName: String) {
        Initial("initial"),
        BudgetContinuation("budget_continuation"),
        StartAdvance("start_advance"),
        StartAdvanceNoRefinedPath("start_advance_no_refined_path"),
        ChunkTopology("chunk_topology"),
        WorldChange("world_change"),
        EdgeObstructed("edge_obstructed"),
        ExplicitRefresh("explicit_refresh"),
        LazyValidation("lazy_validation"),
        ApproachValidation("approach_validation"),
        /** Discovery retrofit compute after the template phase ends. */
        DiscoveryPhase("discovery_phase"),
        /** Adopted-route maneuver-shortcut improvement pass. */
        Improvement("improvement"),
    }

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
        fun trimTo(maxNodes: Int, maxEdges: Int): LazyGraphSnapshot {
            val keptNodes = nodes.take(maxNodes.coerceAtLeast(0))
            val keptSet = keptNodes.asSequence().map { it.pos }.toHashSet()
            val keptEdges = edges.asSequence()
                .filter { it.from in keptSet && it.to in keptSet }
                .take(maxEdges.coerceAtLeast(0))
                .toList()
            return copy(
                nodes = keptNodes,
                edges = keptEdges,
                truncated = truncated || keptNodes.size < nodes.size || keptEdges.size < edges.size,
            )
        }

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

/** Exact identity of one directed planner edge. */
internal data class EdgeKey(val from: FastVector, val to: FastVector)

/**
 * Finds a same-level stance whose block top overlaps the player's 0.6-wide
 * footprint. This is intentionally recovery-only: normal D* start advance
 * remains exact, while a failed jump landing on a block edge can still
 * acquire a valid graph node and route out.
 */
/**
 * Index i such that the player (at block center) lies on refined segment
 * i → i+1. Mid-segment travel stays anchored to the segment head, shared
 * nodes resolve forward, and an exact final endpoint resolves to lastIndex.
 */
internal fun refinedRouteAnchorIndex(refined: List<FastVector>, playerBlock: FastVector): Int? {
    var best: Int? = null
    val px = playerBlock.x + 0.5
    val pz = playerBlock.z + 0.5
    for (i in 0 until refined.lastIndex) {
        val a = refined[i]
        val b = refined[i + 1]
        if (playerBlock.y != a.y && playerBlock.y != b.y) continue
        val ax = a.x + 0.5
        val az = a.z + 0.5
        val dx = (b.x - a.x).toDouble()
        val dz = (b.z - a.z).toDouble()
        val lengthSq = dx * dx + dz * dz
        if (lengthSq < 1.0E-9) continue
        val t = ((px - ax) * dx + (pz - az) * dz) / lengthSq
        if (t < -0.05 || t > 1.05) continue
        val lateral = kotlin.math.abs((px - ax) * dz - (pz - az) * dx) / sqrt(lengthSq)
        if (lateral > START_ADVANCE_CORRIDOR) continue
        // The final endpoint has no following segment to win the furthest
        // match, so promote an exact stance there explicitly.
        best = if (i == refined.lastIndex - 1 && playerBlock == b) i + 1 else i
    }
    return best
}

internal fun footprintSupportedStance(
    view: com.lambda.worldview.WorldView,
    playerPosition: net.minecraft.util.math.Vec3d,
    playerBlock: BlockPos,
): FastVector? {
    var best: FastVector? = null
    var bestDistanceSq = Double.POSITIVE_INFINITY
    for (x in playerBlock.x - 1..playerBlock.x + 1) {
        val dx = x + 0.5 - playerPosition.x
        if (kotlin.math.abs(dx) > PLAYER_FOOTPRINT_SUPPORT_REACH) continue
        for (z in playerBlock.z - 1..playerBlock.z + 1) {
            val dz = z + 0.5 - playerPosition.z
            if (kotlin.math.abs(dz) > PLAYER_FOOTPRINT_SUPPORT_REACH) continue
            if (!MoveTable.isStance(view, x, playerBlock.y, z)) continue
            val distanceSq = dx * dx + dz * dz
            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq
                best = com.lambda.util.world.fastVectorOf(x, playerBlock.y, z)
            }
        }
    }
    return best
}

// Half a block top + half the player's 0.6-wide footprint, plus epsilon.
private const val PLAYER_FOOTPRINT_SUPPORT_REACH = 0.801
private const val START_ADVANCE_CORRIDOR = 1.5
