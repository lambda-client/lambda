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

package com.lambda.pathing.core

import kotlin.math.abs
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Lazy D* Lite planner over a [LazyGraph].
 */
class DStarLite<N>(
    private val graph: LazyGraph<N>,
    start: N,
    val goal: N,
    private val heuristic: (N, N) -> Double,
    /**
     * Optional deterministic tie-breaker between equal-cost successors during
     * [path] extraction. Without this, ties resolve via HashMap iteration order
     * which can flip across calls and produce visually different but
     * equivalent-cost paths. That instability cascades into refinement.
     */
    private val nodeTieBreaker: Comparator<N>? = null,
) {
    var start: N = start
        private set

    private val gValues = HashMap<N, Double>()
    private val rhsValues = HashMap<N, Double>()

    val queue = UpdatablePriorityQueue<N, Key>()

    var km = 0.0
        private set

    init {
        initialize()
    }

    fun initialize(clearGraph: Boolean = true) {
        queue.clear()
        km = 0.0
        gValues.clear()
        rhsValues.clear()
        if (clearGraph) graph.clear()

        setRhs(goal, 0.0)
        queue.insert(goal, Key(heuristic(start, goal), 0.0))
    }

    /**
     * Repairs the shortest-path tree until [start] is locally consistent or the
     * time budget is exhausted.
     *
     * @return metrics describing how much work was completed.
     */
    fun computeShortestPath(timeBudget: Duration = 500.milliseconds): ComputeResult {
        val deadline = System.nanoTime() + timeBudget.inWholeNanoseconds
        var processed = 0
        var timedOut = false

        while (shouldCompute()) {
            if (System.nanoTime() > deadline) {
                timedOut = true
                break
            }

            val oldKey = queue.topKey(Key.INFINITY)
            val node = queue.top()
            val newKey = calculateKey(node)

            when {
                oldKey < newKey -> queue.update(node, newKey)

                g(node) > rhs(node) -> {
                    setG(node, rhs(node))
                    queue.remove(node)
                    graph.predecessors(node).forEach { (predecessor, cost) ->
                        if (predecessor != goal) setRhs(predecessor, min(rhs(predecessor), cost + g(node)))
                        updateVertex(predecessor)
                    }
                }

                else -> {
                    val oldG = g(node)
                    setG(node, INF)

                    (graph.predecessors(node).keys + node).forEach { predecessor ->
                        if (predecessor != goal && sameCost(rhs(predecessor), graph.cost(predecessor, node) + oldG)) {
                            setRhs(predecessor, minSuccessorCost(predecessor))
                        }
                        updateVertex(predecessor)
                    }
                }
            }

            processed++
        }

        return ComputeResult(processed, timedOut)
    }

    fun updateStart(newStart: N) {
        if (newStart == start) return
        val previousStart = start
        start = newStart
        km += heuristic(previousStart, newStart)
    }

    /**
     * Synchronizes already-known graph nodes against current successor generation.
     *
     * This is the core hook for keeping the lazy graph in sync with world-change
     * transactions without mirroring the entire world. Unknown nodes are ignored;
     * they will be generated from current world state if planning reaches them.
     */
    fun synchronizeAffected(affectedNodes: Iterable<N>): SynchronizationResult {
        var nodesChecked = 0
        var edgesAdded = 0
        var edgesRemoved = 0
        var edgesChanged = 0

        affectedNodes.toSet().forEach { node ->
            val knownView = graph.knownSuccessors(node)
            if (node !in graph && knownView.isEmpty()) return@forEach

            // Snapshot before any updateEdge mutates the underlying view.
            val oldSuccessors = if (knownView.isEmpty()) emptyMap() else HashMap(knownView)
            val newSuccessors = graph.generateSuccessors(node)
            graph.markSuccessorsInitialized(node)
            nodesChecked++

            (oldSuccessors.keys - newSuccessors.keys).forEach { removed ->
                updateEdge(node, removed, INF)
                edgesRemoved++
            }

            newSuccessors.forEach { (successor, newCost) ->
                val oldCost = oldSuccessors[successor]
                when {
                    oldCost == null -> {
                        updateEdge(node, successor, newCost)
                        edgesAdded++
                    }
                    !sameCost(oldCost, newCost) -> {
                        updateEdge(node, successor, newCost)
                        edgesChanged++
                    }
                }
            }
        }

        return SynchronizationResult(nodesChecked, edgesAdded, edgesRemoved, edgesChanged)
    }

    fun updateEdge(from: N, to: N, newCost: Double) {
        val oldCost = graph.cost(from, to)
        graph.setCost(from, to, newCost)

        when {
            oldCost > newCost -> if (from != goal) setRhs(from, min(rhs(from), newCost + g(to)))
            sameCost(rhs(from), oldCost + g(to)) -> if (from != goal) setRhs(from, minSuccessorCost(from))
        }

        updateVertex(from)
    }

    fun path(maxLength: Int = 10_000): List<N> {
        if (rhs(start) == INF) return emptyList()

        val path = ArrayList<N>()
        val seen = HashSet<N>()
        var current = start

        path += current
        seen += current

        repeat(maxLength) {
            if (current == goal) return path
            val successors = graph.successors(current)
            if (successors.isEmpty()) return path
            val next = if (nodeTieBreaker != null) {
                successors.entries.minWithOrNull(
                    Comparator { a, b ->
                        val cmp = (a.value + g(a.key)).compareTo(b.value + g(b.key))
                        if (cmp != 0) cmp else nodeTieBreaker.compare(a.key, b.key)
                    }
                )?.key
            } else {
                successors.minByOrNull { (successor, cost) -> cost + g(successor) }?.key
            } ?: return path
            if (!seen.add(next)) return path
            path += next
            current = next
        }

        return path
    }

    fun g(node: N): Double = gValues[node] ?: INF

    fun rhs(node: N): Double = rhsValues[node] ?: INF

    fun key(node: N): Key = calculateKey(node)

    fun isQueued(node: N): Boolean = node in queue

    fun updateVertex(node: N) {
        val inQueue = node in queue
        val inconsistent = !sameCost(g(node), rhs(node))

        when {
            inconsistent && inQueue -> queue.update(node, calculateKey(node))
            inconsistent && !inQueue -> queue.insert(node, calculateKey(node))
            !inconsistent && inQueue -> queue.remove(node)
        }
    }

    private fun shouldCompute() = queue.topKey(Key.INFINITY) < calculateKey(start) || !sameCost(rhs(start), g(start))

    private fun calculateKey(node: N): Key {
        val minCost = min(g(node), rhs(node))
        return Key(minCost + heuristic(start, node) + km, minCost)
    }

    private fun minSuccessorCost(node: N) = graph.successors(node).minOfOrNull { (successor, cost) -> cost + g(successor) } ?: INF

    private fun setG(node: N, value: Double) {
        if (value == INF) gValues.remove(node) else gValues[node] = value
    }

    private fun setRhs(node: N, value: Double) {
        if (value == INF) rhsValues.remove(node) else rhsValues[node] = value
    }

    data class ComputeResult(
        val processedNodes: Int,
        val timedOut: Boolean,
    )

    data class SynchronizationResult(
        val nodesChecked: Int,
        val edgesAdded: Int,
        val edgesRemoved: Int,
        val edgesChanged: Int,
    )

    companion object {
        private const val INF = Double.POSITIVE_INFINITY
        private const val EPSILON = 1e-9

        private fun sameCost(a: Double, b: Double, tolerance: Double = EPSILON): Boolean = when {
            a.isInfinite() || b.isInfinite() -> a == b
            else -> abs(a - b) <= tolerance
        }
    }
}
