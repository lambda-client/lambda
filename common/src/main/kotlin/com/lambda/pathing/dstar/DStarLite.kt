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

package com.lambda.pathing.dstar

import com.lambda.graphics.gl.Matrices
import com.lambda.graphics.gl.Matrices.buildWorldProjection
import com.lambda.graphics.gl.Matrices.withVertexTransform
import com.lambda.graphics.renderer.gui.FontRenderer
import com.lambda.graphics.renderer.gui.FontRenderer.drawString
import com.lambda.pathing.PathingSettings
import com.lambda.util.math.Vec2d
import com.lambda.util.math.div
import com.lambda.util.math.plus
import com.lambda.util.world.FastVector
import com.lambda.util.world.string
import com.lambda.util.world.toCenterVec3d
import kotlin.math.min

/**
 * Lazy D* Lite Implementation.
 *
 * We perform a backward search from the goal to the start, so:
 *  - rhs(goal) = 0, g(goal) = ∞
 *  - "start" is the agent's current location from which we want a path *to* the goal
 *  - 'km' accumulates the heuristic shift so we don't reorder the entire queue after each move
 *
 * @param graph      The graph on which we plan (with forward + reverse adjacency).
 * @param heuristic  A consistent (or at least nonnegative) heuristic function h(a,b).
 * @param start      The agent's current position.
 * @param goal       The fixed goal vertex.
 */
class DStarLite(
    private val graph: LazyGraph,
    var start: FastVector,
    private val goal: FastVector,
    private val heuristic: (FastVector, FastVector) -> Double,
) {
    // gMap[u], rhsMap[u] store g(u) and rhs(u) or default to ∞ if not present
    private val gMap = mutableMapOf<FastVector, Double>()
    private val rhsMap = mutableMapOf<FastVector, Double>()

    // Priority queue holding inconsistent vertices.
    private val U = PriorityQueueDStar<FastVector>()

    // km accumulates heuristic differences as the start changes.
    private var km = 0.0

    init {
        initialize()
    }

    /**
     * Initialize D* Lite:
     *  - g(goal)=∞, rhs(goal)=0
     *  - Insert goal into U with key = calculateKey(goal).
     */
    fun initialize() {
        gMap.clear()
        rhsMap.clear()
        setG(goal, INF)
        setRHS(goal, 0.0)
        U.insertOrUpdate(goal, calculateKey(goal))
    }

    fun g(u: FastVector): Double = gMap[u] ?: INF
    private fun setG(u: FastVector, g: Double) { gMap[u] = g }

    fun rhs(u: FastVector): Double = rhsMap[u] ?: INF
    private fun setRHS(u: FastVector, rhs: Double) { rhsMap[u] = rhs }

    /**
     * Calculates the key for vertex u.
     *   Key(u) = ( min(g(u), rhs(u)) + h(start, u) + km, min(g(u), rhs(u)) )
     */
    private fun calculateKey(u: FastVector): Key {
        val minGRHS = min(g(u), rhs(u))
        return Key(minGRHS + heuristic(start, u) + km, minGRHS)
    }

    /**
     * Updates the vertex u.
     *
     * If u != goal, then:
     *   rhs(u) = min_{v in Pred(u)} [g(v) + cost(v,u)]
     *
     * Then u is removed from the queue and reinserted if it is inconsistent.
     */
    private fun updateVertex(u: FastVector) {
        if (u != goal) {
            var tmp = INF
            graph.predecessors(u).forEach { (pred, cost) ->
                val candidate = g(pred) + cost
                if (candidate < tmp) {
                    tmp = candidate
                }
            }
            setRHS(u, tmp)
        }
        U.remove(u)
        if (g(u) != rhs(u)) {
            U.insertOrUpdate(u, calculateKey(u))
        }
    }

    fun invalidate(u: FastVector) {
        val affectedNodes = graph.getNeighbors(u) + u
        affectedNodes.forEach { v ->
            graph.invalidate(v)
            updateVertex(v)
        }
    }

    /**
     * Propagates changes until the start is locally consistent.
     * computeShortestPath():
     *   While the queue top is "less" than calculateKey(start)
     *   or g(start) < rhs(start), pop and process.
     */
    fun computeShortestPath(cutoffTimeout: Long = 500L) {
        val startTime = System.currentTimeMillis()

        fun timedOut() = (System.currentTimeMillis() - startTime) > cutoffTimeout
        fun shouldUpdateState() = U.topKey() < calculateKey(start) || g(start) < rhs(start)

        while (shouldUpdateState() && !timedOut()) {
            val u = U.top() ?: break
            val oldKey = U.topKey()
            val newKey = calculateKey(u)

            if (oldKey < newKey) {
                // Priority out-of-date; update it
                U.insertOrUpdate(u, newKey)
            } else {
                U.pop()
                if (g(u) > rhs(u)) {
                    // We found a better path for u
                    setG(u, rhs(u))
                    graph.successors(u).forEach { (succ, _) ->
                        updateVertex(succ)
                    }
                } else {
                    val oldG = g(u)
                    setG(u, INF)
                    updateVertex(u)
                    // Update successors that may have relied on old g(u)
                    graph.successors(u).forEach { (succ, _) ->
                        if (rhs(succ) == oldG + graph.cost(u, succ)) {
                            updateVertex(succ)
                        }
                    }
                }
            }
        }
    }

    /**
     * When the agent moves, update the start.
     * The variable km is increased by h(oldStart, newStart) and
     * all vertices in the queue are re-keyed.
     */
    fun updateStart(newStart: FastVector) {
        if (newStart == start) return
        val oldStart = start
        start = newStart
        km += heuristic(oldStart, start)
        val tmpList = mutableListOf<FastVector>()
        while (!U.isEmpty()) {
            U.top()?.let { tmpList.add(it) }
            U.pop()
        }
        tmpList.forEach { v ->
            U.insertOrUpdate(v, calculateKey(v))
        }
    }

    /**
     * Retrieves a path from start to goal by always choosing the successor
     * with the lowest g + cost value. If no path is found, the path stops early.
     */
    fun path(maxLength: Int = 10_000): List<FastVector> {
        val path = mutableListOf<FastVector>()

        if (!graph.contains(start)) return path.toList()

        var current = start
        path.add(current)
        while (current != goal) {
            val successors = graph.successors(current)
            if (successors.isEmpty()) break
            var bestNext: FastVector? = null
            var bestVal = INF
            for ((succ, cost) in successors) {
                val candidate = g(succ) + cost
                if (candidate < bestVal) {
                    bestVal = candidate
                    bestNext = succ
                }
            }
            // No path
            if (bestNext == null) break
            current = bestNext
            if (current !in path) {
                path.add(current)
            } else {
                break
            }
            if (path.size > maxLength) break
        }
        return path
    }

    fun buildDebugInfoRenderer(config: PathingSettings) {
        if (!config.renderGraph) return
        val mode = Matrices.ProjRotationMode.TO_CAMERA
        val scale = config.fontScale
        graph.nodes.take(config.maxRenderObjects).forEach { origin ->
            val label = mutableListOf<String>()
            if (config.renderPositions) label.add(origin.string)
            if (config.renderG) label.add("g: %.3f".format(g(origin)))
            if (config.renderRHS) label.add("rhs: %.3f".format(rhs(origin)))

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
                    val center = (centerO + centerN) / 2.0
                    val projection = buildWorldProjection(center, scale, mode)
                    withVertexTransform(projection) {
                        val msg = "c: %.3f".format(cost)
                        drawString(msg, Vec2d(-FontRenderer.getWidth(msg) * 0.5, 0.0))
                    }
                }
            }
        }
    }

    override fun toString() = buildString {
        appendLine("Nodes:")
        graph.nodes.forEach { appendLine("  ${it.string} g: ${g(it)} rhs: ${rhs(it)}") }
        appendLine("Edges:")
        graph.nodes.forEach { u ->
            graph.successors(u).forEach { (v, cost) ->
                appendLine("  ${u.string} -> ${v.string}: c: $cost")
            }
        }
    }

    companion object {
        private const val INF = Double.POSITIVE_INFINITY
    }
}