/*
 * Copyright 2025 Lambda
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

package com.lambda.pathing.incremental

import com.lambda.graphics.gl.Matrices
import com.lambda.graphics.gl.Matrices.buildWorldProjection
import com.lambda.graphics.gl.Matrices.withVertexTransform
import com.lambda.graphics.renderer.gui.FontRenderer
import com.lambda.graphics.renderer.gui.FontRenderer.drawString
import com.lambda.pathing.PathingSettings
import com.lambda.util.GraphUtil
import com.lambda.util.math.Vec2d
import com.lambda.util.math.minus
import com.lambda.util.math.plus
import com.lambda.util.math.times
import com.lambda.util.world.FastVector
import com.lambda.util.world.string
import com.lambda.util.world.toCenterVec3d
import kotlin.math.abs
import kotlin.math.min

/**
 * Lazy D* Lite Implementation.
 *
 * @param graph      The LazyGraph on which we plan.
 * @param start      The agent's initial position.
 * @param goal       The fixed goal vertex.
 * @param heuristic  A consistent (or at least nonnegative) heuristic function h(a,b).
 */
class DStarLite(
    private val graph: LazyGraph,
    var start: FastVector,
    val goal: FastVector,
    val heuristic: (FastVector, FastVector) -> Double,
) {
    // gMap[u], rhsMap[u] store g(u) and rhs(u) or default to ∞ if not present
    private val gMap = mutableMapOf<FastVector, Double>()
    private val rhsMap = mutableMapOf<FastVector, Double>()

    // Priority queue holding inconsistent vertices.
    val U = UpdatablePriorityQueue<FastVector, Key>()

    // km accumulates heuristic differences as the start changes.
    var km = 0.0

    init {
        initialize()
    }

    /** Re-initialize the algorithm. */
    fun initialize() {
        U.clear()
        km = 0.0
        gMap.clear()
        rhsMap.clear()
        graph.clear()

        setRHS(goal, 0.0)
        U.insert(goal, Key(heuristic(start, goal), 0.0))
    }

    /**
     * Computes the shortest path from the start node to the goal node using the D* Lite algorithm.
     * Updates the priority queue and node values iteratively until consistency is achieved or the operation times out.
     *
     * @param cutoffTimeout The maximum amount of time (in milliseconds) allowed for the computation to run before timing out. Defaults to 500ms.
     */
    fun computeShortestPath(cutoffTimeout: Long = 500L) {
        val startTime = System.currentTimeMillis()

        fun timedOut() = (System.currentTimeMillis() - startTime) > cutoffTimeout

        fun checkCondition() = U.topKey(Key.INFINITY) < calculateKey(start) || rhs(start) > g(start)

        while (checkCondition() && !timedOut()) {
            val u = U.top() // Get node with smallest key
            val kOld = U.topKey(Key.INFINITY) // Key before potential update
            val kNew = calculateKey(u) // Recalculate key

            when {
                // Case 1: Key increased (inconsistency detected or km changed priority)
                kOld < kNew -> {
                    U.update(u, kNew)
                }
                // Case 2: Overconsistent state (g > rhs) -> Make consistent
                g(u) > rhs(u) -> {
                    setG(u, rhs(u)) // Set g = rhs
                    U.remove(u) // Remove from queue, now consistent (g=rhs)
                    // Propagate change to predecessors s
                    graph.predecessors(u).forEach { (s, c) ->
                        if (s != goal) setRHS(s, min(rhs(s), graph.cost(s, u) + g(u)))
                        updateVertex(s)
                    }
                }
                // Case 3: Underconsistent state (g <= rhs but needs update, implies g < ∞)
                // Typically g < rhs, but equality handled by removal in updateVertex.
                // Here g is likely outdatedly low. Set g = ∞ and update neighbors.
                else -> {
                    val gOld = g(u)
                    setG(u, INF)

                    (graph.predecessors(u).keys + u).forEach { s ->
                        // If rhs(s) was based on the old g(u) path cost
                        if (rhs(s) == graph.cost(s, u) + gOld && s != goal) {
                            // Recalculate rhs(s) based on its *current* successors' g-values
                            setRHS(s, minSuccessorCost(s))
                        }
                        updateVertex(s) // Check consistency of s
                    }
                }
            }
        }
    }

    /**
     * Updates the starting point of the pathfinding algorithm to a new position.
     * If the new starting point is different from the current one, the heuristic cost (`km`) is updated
     * to account for the change in path distance.
     *
     * @param newStart The new starting position represented by a `FastVector`.
     */
    fun updateStart(newStart: FastVector) {
        if (newStart == start) return
        val lastStart = start
        start = newStart
        km += heuristic(lastStart, start)
    }

    /**
     * Invalidates a node and updates affected neighbors.
     * Also updates the neighbors of neighbors to ensure diagonal paths are correctly recalculated.
     * Optionally prunes the graph after invalidation to remove unnecessary nodes and edges.
     * 
     * @param u The node to invalidate
     */
    fun invalidate(u: FastVector, prune: Boolean = false) {
        val modified = mutableSetOf(u)
        (GraphUtil.n26(u).keys + u).forEach { v ->
            val current = graph.neighbors(v)
            val updated = graph.nodeInitializer(v)
            val removed = current.filter { w -> w !in updated }
            removed.forEach { w ->
                updateEdge(v, w, INF)
                updateEdge(w, v, INF)
            }
            updated.forEach { (w, c) ->
                updateEdge(v, w, c)
                updateEdge(w, v, c)
            }
            modified.addAll(removed + updated.keys + v)
        }
        if (prune) prune(modified)
    }

    private fun prune(modifiedNodes: Set<FastVector> = emptySet()) {
        graph.prune(modifiedNodes).forEach {
            gMap.remove(it)
            rhsMap.remove(it)
        }
    }

    /**
     * Updates the cost of an edge between two nodes in the graph and adjusts the algorithm's state accordingly.
     *
     * @param u The starting node of the edge to update.
     * @param v The ending node of the edge to update.
     * @param c The new cost value to set for the edge.
     */
    fun updateEdge(u: FastVector, v: FastVector, c: Double) {
        val cOld = graph.cost(u, v)
        graph.setCost(u, v, c)
        when {
            cOld > c -> if (u != goal) setRHS(u, min(rhs(u), c + g(v)))
            rhs(u) == cOld + g(v) -> if (u != goal) setRHS(u, minSuccessorCost(u))
        }
        updateVertex(u)
    }

    /**
     * Retrieves a path from start to goal by always choosing the successor
     * with the lowest `g(successor) + cost(current, successor)` value.
     * If no path is found (INF cost), the path stops early.
     * 
     * @param maxLength The maximum number of nodes to include in the path
     * @return A list of nodes representing the path from start to goal
     */
    fun path(maxLength: Int = 10_000): List<FastVector> {
        val path = mutableListOf<FastVector>()
        if (start !in graph) return emptyList()
        if (rhs(start) == INF) return emptyList()

        var current = start
        path.add(current)

        var iterations = 0
        while (current != goal && iterations < maxLength) {
            iterations++
            val cheapest = graph.successors(current)
                .minByOrNull { (succ, cost) -> cost + g(succ) } ?: break
            current = cheapest.key
            if (current !in path) path.add(current) else break
        }
        return path
    }

    /** Provides the calculated g-value for a node (cost from start). INF if unknown/unreachable. */
    fun g(u: FastVector): Double = gMap[u] ?: INF

    /** Provides the calculated rhs-value for a node. INF if unknown/unreachable. */
    fun rhs(u: FastVector): Double = rhsMap[u] ?: INF

    private fun setG(u: FastVector, gVal: Double) {
        if (gVal == INF) gMap.remove(u) else gMap[u] = gVal
    }

    private fun setRHS(u: FastVector, rhsVal: Double) {
        if (rhsVal == INF) rhsMap.remove(u) else rhsMap[u] = rhsVal
    }

    /** Internal key calculation using current start and km. */
    private fun calculateKey(s: FastVector): Key {
        val minGRHS = min(g(s), rhs(s))
        return Key(minGRHS + heuristic(start, s) + km, minGRHS)
    }

    /** Updates a vertex's state in the priority queue based on its consistency (g vs rhs). */
    fun updateVertex(u: FastVector) {
        val uInQueue = u in U
        when {
            // Inconsistent and in Queue: Update priority
            g(u) != rhs(u) && uInQueue -> {
                U.update(u, calculateKey(u))
            }
            // Inconsistent and not in Queue: Insert
            g(u) != rhs(u) && !uInQueue -> {
                U.insert(u, calculateKey(u))
            }
            // Consistent and in Queue: Remove
            g(u) == rhs(u) && uInQueue -> {
                U.remove(u)
            }
            // Consistent and not in Queue: Do nothing
        }
    }

    /** Computes min_{s' in Succ(s)} (c(s, s') + g(s')). */
    private fun minSuccessorCost(s: FastVector) =
        graph.successors(s).minOfOrNull { (s1, cost) -> cost + g(s1) } ?: INF

    fun buildDebugInfoRenderer(config: PathingSettings) {
        if (!config.renderGraph) return
        val mode = Matrices.ProjRotationMode.TO_CAMERA
        val scale = config.fontScale
        graph.nodes.take(config.maxRenderObjects).forEach { origin ->
            val label = mutableListOf<String>()
            if (config.renderPositions) label.add(origin.string)
            if (config.renderG) label.add("g: %.3f".format(g(origin)))
            if (config.renderRHS) label.add("rhs: %.3f".format(rhs(origin)))
            if (config.renderKey) label.add("k: ${calculateKey(origin)}")
            if (config.renderQueue && origin in U) label.add("QUEUED")

            if (label.isNotEmpty()) {
                val pos = origin.toCenterVec3d()
                val projection = buildWorldProjection(pos, scale, mode)
                withVertexTransform(projection) {
                    var height = -0.5 * label.size * (FontRenderer.getHeight() + 2)

                    label.forEach {
                        drawString(it, Vec2d(-FontRenderer.getWidth(it) * 0.5, height))
                        height += FontRenderer.getHeight() + 2
                    }
                }
            }

            if (config.renderCost) {
                graph.successors[origin]?.forEach { (neighbor, cost) ->
                    val centerO = origin.toCenterVec3d()
                    val centerN = neighbor.toCenterVec3d()
                    val center = centerO + (centerN - centerO) * (1.0 / 3.0)
                    val projection = buildWorldProjection(center, scale, mode)
                    withVertexTransform(projection) {
                        val msg = "sc: %.3f".format(cost)
                        drawString(msg, Vec2d(-FontRenderer.getWidth(msg) * 0.5, 0.0))
                    }
                }
            }
        }
    }

    /**
     * Verifies that the current graph is consistent with a freshly generated graph.
     * This is useful for ensuring that incremental updates maintain correctness.
     */
    fun compareWith(
        other: DStarLite
    ): Pair<LazyGraph.GraphDifferences, Set<ValueDifference>> {
        // Compare edge consistency between the two graphs
        val graphDifferences = graph.compareWith(other.graph)

        // Compare g and rhs values for common nodes
        val commonNodes = graph.nodes.intersect(other.graph.nodes)
        val wrong = mutableSetOf<ValueDifference>()

        commonNodes.forEach { node ->
            val g1 = g(node)
            val g2 = other.g(node)

            if (abs(g1 - g2) > 1e-6) {
                wrong.add(ValueDifference(ValueDifference.Value.G, g1, g2))
            }

            val rhs1 = rhs(node)
            val rhs2 = other.rhs(node)

            if (abs(rhs1 - rhs2) > 1e-6) {
                wrong.add(ValueDifference(ValueDifference.Value.RHS, rhs1, rhs2))
            }
        }

        return Pair(graphDifferences, wrong)
    }

    data class ValueDifference(
        val type: Value,
        val v1: Double,
        val v2: Double,
    ) {
        enum class Value { G, RHS }
        override fun toString() = "${type.name} is $v1 but should be $v2"
    }

    override fun toString() = buildString {
        appendLine("D* Lite State:")
        appendLine("Start: ${start.string}, Goal: ${goal.string}, k_m: $km")
        appendLine("Queue Size: ${U.size()}")
        if (!U.isEmpty()) {
            appendLine("Top Key: ${U.topKey(Key.INFINITY)}, Top Node: ${U.top().string}")
        }
        appendLine("Graph Size: ${graph.size}")
        appendLine("Known Nodes (${graph.nodes.size}):")
        val show = 30
        graph.nodes.take(show).forEach {
            appendLine("  ${it.string} g: ${"%.2f".format(g(it))}, rhs: ${"%.2f".format(rhs(it))}, key: ${calculateKey(it)}")
        }
        if (graph.nodes.size > show) appendLine("  ... (${graph.nodes.size - show} more nodes)")
    }

    companion object {
        private const val INF = Double.POSITIVE_INFINITY
    }
}
