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
    private val graph: Graph,
    private val heuristic: (Int, Int) -> Double,
    var start: Int,
    val goal: Int
) {
    private val INF = Double.POSITIVE_INFINITY

    // gMap[u], rhsMap[u] store g(u) and rhs(u) or default to ∞ if not present
    private val gMap = mutableMapOf<Int, Double>()
    private val rhsMap = mutableMapOf<Int, Double>()

    // Priority queue
    private val U = PriorityQueueDStar()

    // Heuristic shift
    private var km = 0.0

    init {
        initialize()
    }

    /**
     * Initialize D* Lite:
     *  - g(goal)=∞, rhs(goal)=0
     *  - Insert goal into U with key = calculateKey(goal).
     */
    private fun initialize() {
        gMap.clear()
        rhsMap.clear()
        setG(goal, INF)
        setRHS(goal, 0.0)
        U.insertOrUpdate(goal, calculateKey(goal))
    }

    private fun g(u: Int): Double = gMap[u] ?: INF
    private fun setG(u: Int, value: Double) {
        gMap[u] = value
    }

    private fun rhs(u: Int): Double = rhsMap[u] ?: INF
    private fun setRHS(u: Int, value: Double) {
        rhsMap[u] = value
    }

    /**
     * Key(u) = ( min(g(u), rhs(u)) + h(start, u) + km ,  min(g(u), rhs(u)) ).
     */
    private fun calculateKey(u: Int): Key {
        val minGRHS = min(g(u), rhs(u))
        return Key(minGRHS + heuristic(start, u) + km, minGRHS)
    }

    /**
     * UpdateVertex(u):
     *  1) If u != goal, rhs(u) = min_{v in Pred(u)} [g(v) + cost(v,u)]
     *  2) Remove u from U
     *  3) If g(u) != rhs(u), insertOrUpdate(u, calculateKey(u))
     */
    fun updateVertex(u: Int) {
        if (u != goal) {
            var tmp = INF
            graph.predecessors(u).forEach { (pred, c) ->
                val valCandidate = g(pred) + c
                if (valCandidate < tmp) {
                    tmp = valCandidate
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
     * computeShortestPath():
     *   While the queue top is "less" than calculateKey(start)
     *   or g(start) < rhs(start), pop and process.
     */
    fun computeShortestPath() {
        while (
            (U.topKey() < calculateKey(start)) ||
            (g(start) < rhs(start))
        ) {
            val u = U.top() ?: break
            val oldKey = U.topKey()
            val newKey = calculateKey(u)

            if (oldKey < newKey) {
                // Priority out-of-date; update it
                U.insertOrUpdate(u, newKey)
            } else {
                // Remove it from queue
                U.pop()
                if (g(u) > rhs(u)) {
                    // We found a better path for u
                    setG(u, rhs(u))
                    // Update successors of u
                    graph.successors(u).forEach { (s, _) ->
                        updateVertex(s)
                    }
                } else {
                    // g(u) <= rhs(u)
                    val gOld = g(u)
                    setG(u, INF)
                    updateVertex(u)
                    // Update successors that may have relied on old g(u)
                    graph.successors(u).forEach { (s, _) ->
                        if (rhs(s) == gOld + graph.cost(u, s)) {
                            updateVertex(s)
                        }
                    }
                }
            }
        }
    }

    /**
     * Called when the robot moves from oldStart to newStart.
     * We increase km by h(oldStart, newStart), then re-key all queued vertices.
     */
    fun updateStart(newStart: Int) {
        val oldStart = start
        start = newStart
        km += heuristic(oldStart, start)

        // Re-key everything in U
        val tmpList = mutableListOf<Int>()
        while (!U.isEmpty()) {
            U.top()?.let { tmpList.add(it) }
            U.pop()
        }
        tmpList.forEach { v ->
            U.insertOrUpdate(v, calculateKey(v))
        }
    }

    /**
     * Returns a path from 'start' to 'goal' by always choosing
     * the successor s of the current vertex that minimizes g(s)+cost(current->s).
     * If no path is found, the path ends prematurely.
     */
    fun getPath(): List<Int> {
        val path = mutableListOf<Int>()
        var current = start
        path.add(current)

        while (current != goal) {
            val successors = graph.successors(current)
            if (successors.isEmpty()) break

            var bestNext: Int? = null
            var bestVal = INF
            for ((s, c) in successors) {
                val valCandidate = g(s) + c
                if (valCandidate < bestVal) {
                    bestVal = valCandidate
                    bestNext = s
                }
            }
            // No path
            if (bestNext == null) break
            current = bestNext
            path.add(current)

            // Safety net to avoid infinite loops
            if (path.size > 100000) break
        }
        return path
    }
}