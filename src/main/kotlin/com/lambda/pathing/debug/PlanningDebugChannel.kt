/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.debug

import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.coarse.Stance
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.trajectory.TrajectoryDiagnostic
import com.lambda.pathing.trajectory.TrajectoryRollout
import net.minecraft.util.math.Vec3d

object PlanningDebugChannel {
    class Attempt(
        val points: List<Vec3d>,
        val certified: Boolean,
        val diagnostic: String?,
    )

    @Volatile
    private var active = false

    @Volatile
    var coarseRoute: CoarseRoutePlan? = null
        private set

    @Volatile
    var attempts: List<Attempt> = emptyList()
        private set

    /**
     * One cell of the search graph, reduced to what can be drawn.
     *
     * [cost] is the D* Lite `g` value: ticks from this cell to the goal along the best
     * route the search currently knows. [frontier] marks a cell still in the open queue --
     * the boundary the search would grow next, and the thing worth looking at when a route
     * fails to appear, because it is exactly where the expansion stopped.
     */
    class GraphNode(
        val pos: Vec3d,
        val cost: Double,
        val frontier: Boolean,
        /** An optimistic step into unstreamed terrain rather than a move over known ground. */
        val anchor: Boolean,
    )

    /**
     * One expanded edge of the search graph: a move the search costed between two cells.
     *
     * [policy] marks the successor the search would actually take out of [from] -- the
     * argmin of edge cost plus the successor's cost to the goal. Those edges chained
     * together are the route D* believes in, so drawing them is drawing the flow field
     * rather than a hairball of everything the expansion happened to touch.
     */
    class GraphEdge(
        val from: Vec3d,
        val to: Vec3d,
        val cost: Double,
        val policy: Boolean,
        /** Cost to the goal at each end, so the draw can shade the edge downhill. */
        val fromCost: Double,
        val toCost: Double,
    )

    /**
     * A bounded window onto the graph, with the range needed to colour it.
     *
     * Bounded because the graph runs to tens of thousands of cells on a long path and the
     * render is per-frame. [total] against [nodes]`.size` is what says so honestly rather
     * than quietly drawing a fraction of the truth.
     */
    class GraphSample(
        val nodes: List<GraphNode>,
        val edges: List<GraphEdge>,
        val cheapest: Double,
        val dearest: Double,
        val total: Int,
        /** Edges the search holds between drawn cells, before the draw cap bit. */
        val totalEdges: Int,
    )

    /**
     * What the graph view is allowed to spend, per publish.
     *
     * Held on the channel rather than read from config at the point of use because the
     * sampling runs on a planning worker while the settings live on the client: taking a
     * copy when the session begins keeps the worker off the config and keeps one publish
     * internally consistent.
     */
    data class GraphViewLimits(
        val radius: Double = 48.0,
        val cells: Int = 3072,
        val edges: Int = 12_288,
    )

    class CandidateLine(val points: List<Vec3d>, val best: Boolean)

    @Volatile
    var candidateLines: List<CandidateLine> = emptyList()
        private set

    fun publishCandidates(lines: List<CandidateLine>) {
        if (active) candidateLines = lines
    }

    @Volatile
    var graph: GraphSample? = null
        private set

    @Volatile
    var graphLimits: GraphViewLimits = GraphViewLimits()
        private set

    /**
     * Samples the search graph around [around], nearest first, capped.
     *
     * Reachable cells are kept ahead of unreachable ones when the cap bites: a cell with
     * no finite cost to the goal is drawn the same everywhere, so spending the budget on
     * it would hide the gradient that is the whole point of the view.
     */
    fun publishGraph(planner: CoarsePlanner, around: Vec3d) {
        if (!active) return
        val nodes = planner.graphNodes
        val total = nodes.size
        val anchors = planner.optimisticAnchors
        val goal = planner.search.goal
        val view = planner.view
        val limits = graphLimits
        val radiusSquared = limits.radius * limits.radius

        val stances = nodes.asSequence()
            .map { stance -> stance to distanceSquared(stance, around) }
            .filter { (_, distance) -> distance <= radiusSquared }
            .sortedWith(compareBy({ !planner.search.g(it.first).isFinite() }, { it.second }))
            .take(limits.cells)
            .map { (stance, _) -> stance }
            .toList()

        val drawn = stances.toHashSet()
        val sampled = stances.map { stance ->
            GraphNode(
                pos = center(view, stance),
                cost = planner.search.g(stance),
                frontier = stance in planner.search.queue,
                anchor = stance in anchors,
            )
        }

        // Both endpoints have to be drawn cells, or an edge would run off to a cell that
        // is not on screen and read as a stray line rather than as connectivity.
        var knownEdges = 0
        val edges = ArrayList<GraphEdge>()
        stances.forEach { from ->
            val successors = planner.knownSuccessorsOf(from)
            // The optimistic step is a fiction the search uses to keep reaching toward
            // terrain it has not streamed. It is not a move over ground, and drawn it
            // would fire a long line at the goal from every frontier cell.
            val real = successors.filterKeys { to -> to in drawn && !(to == goal && from in anchors) }
            if (real.isEmpty()) return@forEach

            val best = real.entries.minByOrNull { (to, cost) -> cost + planner.search.g(to) }
                ?.takeIf { (to, cost) -> (cost + planner.search.g(to)).isFinite() }
                ?.key

            knownEdges += real.size
            if (edges.size >= limits.edges) return@forEach
            val fromCost = planner.search.g(from)
            real.forEach { (to, cost) ->
                if (edges.size < limits.edges) {
                    edges += GraphEdge(
                        from = center(view, from),
                        to = center(view, to),
                        cost = cost,
                        policy = to == best,
                        fromCost = fromCost,
                        toCost = planner.search.g(to),
                    )
                }
            }
        }

        val finite = sampled.map { it.cost }.filter { it.isFinite() }
        graph = GraphSample(
            nodes = sampled,
            edges = edges,
            cheapest = finite.minOrNull() ?: 0.0,
            dearest = finite.maxOrNull() ?: 0.0,
            total = total,
            totalEdges = knownEdges,
        )
    }

    /**
     * A graph cell drawn at the height a body standing in it would rest.
     *
     * The nominal height is the cell's floor, which is the standing surface only when the
     * support is a whole block. Over a carpet or a fence the plate floated with nothing
     * under it, which reads as the search having gone somewhere it did not.
     */
    private fun center(view: CoarseVoxelView, stance: Stance) = Vec3d(
        stance.x + 0.5,
        stance.y + view.surfaceOffset(stance.x, stance.y - 1, stance.z),
        stance.z + 0.5,
    )

    private fun distanceSquared(stance: Stance, around: Vec3d): Double {
        val dx = stance.x + 0.5 - around.x
        val dy = stance.y - around.y
        val dz = stance.z + 0.5 - around.z
        return dx * dx + dy * dy + dz * dz
    }

    private val ring = ArrayDeque<Attempt>()

    fun begin(enabled: Boolean, limits: GraphViewLimits = GraphViewLimits()) {
        graphLimits = limits
        synchronized(ring) { ring.clear() }
        attempts = emptyList()
        candidateLines = emptyList()
        // The graph and route views survive into the next session on purpose: the
        // underlying coarse graph is retained across legs, and clearing the render
        // here made every replan look like the graph was lost and rebuilt. The next
        // publishGraph/publishRoute replaces them in place.
        active = enabled
    }

    fun publishRoute(route: CoarseRoutePlan) {
        if (active) coarseRoute = route
    }

    fun publishAttempt(rollout: TrajectoryRollout, certified: Boolean, diagnostic: TrajectoryDiagnostic?) {
        if (!active) return
        val points = ArrayList<Vec3d>(rollout.frames.size / DECIMATION + 2)
        points += rollout.initialState.position
        for (index in rollout.frames.indices step DECIMATION) {
            points += rollout.frames[index].state.position
        }
        rollout.frames.lastOrNull()?.let { last ->
            if ((rollout.frames.size - 1) % DECIMATION != 0) points += last.state.position
        }
        val attempt = Attempt(points, certified, diagnostic?.let { it::class.simpleName })
        synchronized(ring) {
            ring += attempt
            while (ring.size > MAX_ATTEMPTS) ring.removeFirst()
            attempts = ArrayList(ring)
        }
    }

    fun reset() {
        active = false
        coarseRoute = null
        attempts = emptyList()
        candidateLines = emptyList()
        graph = null
        synchronized(ring) { ring.clear() }
    }

    private const val MAX_ATTEMPTS = 32

    private const val DECIMATION = 2
}
