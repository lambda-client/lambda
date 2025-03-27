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

import com.lambda.util.world.FastVector
import kotlin.math.min

/**
 * D* Lite Implementation.
 *
 * We perform a backward search from the goal to the start, so:
 *  - rhs(goal) = 0, g(goal) = ∞
 *  - "start" is the robot's current location from which we want a path *to* the goal
 *  - 'km' accumulates the heuristic shift so we don't reorder the entire queue after each move
 *
 * @param graph      The graph on which we plan (with forward + reverse adjacency).
 * @param heuristic  A consistent (or at least nonnegative) heuristic function h(a,b).
 * @param start      The robot's current position.
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

    private fun g(u: FastVector): Double = gMap[u] ?: INF
    private fun setG(u: FastVector, value: Double) { gMap[u] = value }

    private fun rhs(u: FastVector): Double = rhsMap[u] ?: INF
    private fun setRHS(u: FastVector, value: Double) { rhsMap[u] = value }

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
    fun updateVertex(u: FastVector) {
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

    /**
     * Propagates changes until the start is locally consistent.
     * computeShortestPath():
     *   While the queue top is "less" than calculateKey(start)
     *   or g(start) < rhs(start), pop and process.
     */
    fun computeShortestPath() {
        while ((U.topKey() < calculateKey(start)) || (g(start) < rhs(start))) {
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
     * When the robot moves, update the start.
     * The variable km is increased by h(oldStart, newStart) and
     * all vertices in the queue are re-keyed.
     */
    fun updateStart(newStart: FastVector) {
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
    fun getPath(): List<FastVector> {
        val path = mutableListOf<FastVector>()
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
            path.add(current)
            if (path.size > 100_000) break
        }
        return path
    }

    companion object {
        private const val INF = Double.POSITIVE_INFINITY
    }
}