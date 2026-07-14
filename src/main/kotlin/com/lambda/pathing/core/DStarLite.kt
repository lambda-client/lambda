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
 *
 * The heuristic must be finite, non-negative, admissible, and consistent for
 * the enabled edge library. Edge costs must be non-negative. The graph must
 * provide true predecessors for directed edges.
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

    var routeVersion = 0L
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
        routeVersion++

        setRhs(goal, 0.0)
        queue.insert(goal, Key(checkedHeuristic(start, goal), 0.0))
    }

    /**
     * Repairs the shortest-path tree until [start] is locally consistent or the
     * time budget is exhausted.
     *
     * @return metrics describing how much work was completed.
     */
    fun computeShortestPath(
        timeBudget: Duration = 500.milliseconds,
        maxExpansions: Int = Int.MAX_VALUE,
    ): ComputeResult {
        require(!timeBudget.isNegative()) { "timeBudget must be non-negative" }
        require(maxExpansions >= 0) { "maxExpansions must be non-negative" }

        val startedAt = System.nanoTime()
        val budgetNanos = timeBudget.inWholeNanoseconds
        var processed = 0
        var timedOut = false
        var expansionLimitReached = false

        while (shouldCompute()) {
            if (processed >= maxExpansions) {
                expansionLimitReached = true
                break
            }
            // nanoTime is observable in profiles on large searches. A D* step
            // is small, so checking every few steps retains a tight budget
            // without paying for a clock read on every expanded node.
            if ((processed and DEADLINE_CHECK_MASK) == 0 && System.nanoTime() - startedAt >= budgetNanos) {
                timedOut = true
                break
            }

            val oldKey = queue.topKey(Key.INFINITY)
            val node = queue.top()
            val newKey = calculateKey(node)

            when {
                oldKey < newKey -> queue.update(node, newKey)

                g(node) > rhs(node) -> {
                    val settledG = rhs(node)
                    setG(node, settledG)
                    queue.remove(node)
                    graph.predecessors(node).forEach { (predecessor, cost) ->
                        if (predecessor != goal) setRhs(predecessor, min(rhs(predecessor), cost + settledG))
                        updateVertex(predecessor)
                    }
                }

                else -> {
                    val oldG = g(node)
                    setG(node, INF)

                    val predecessors = graph.predecessors(node)
                    for (predecessor in predecessors.keys) {
                        if (predecessor != goal && sameCost(rhs(predecessor), graph.cost(predecessor, node) + oldG)) {
                            setRhs(predecessor, minSuccessorCost(predecessor))
                        }
                        updateVertex(predecessor)
                    }
                    // D* Lite also updates the popped node. Avoid allocating
                    // `predecessors.keys + node`, while preserving self-loop behavior.
                    if (node !in predecessors) {
                        if (node != goal && sameCost(rhs(node), graph.cost(node, node) + oldG)) {
                            setRhs(node, minSuccessorCost(node))
                        }
                        updateVertex(node)
                    }
                }
            }

            processed++
        }

        return ComputeResult(
            processedNodes = processed,
            timedOut = timedOut,
            expansionLimitReached = expansionLimitReached,
            converged = !shouldCompute(),
        )
    }

    fun updateStart(newStart: N) {
        if (newStart == start) return
        val previousStart = start
        start = newStart
        km += checkedHeuristic(previousStart, newStart)
        routeVersion++
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

        val uniqueNodes = affectedNodes.toHashSet()
        for (node in uniqueNodes) {
            val knownView = graph.knownSuccessors(node)
            if (node !in graph && knownView.isEmpty()) continue

            // Snapshot before any updateEdge mutates the underlying view.
            val oldSuccessors = if (knownView.isEmpty()) emptyMap() else HashMap(knownView)
            val newSuccessors = graph.generateSuccessors(node)
            graph.markSuccessorsInitialized(node)
            nodesChecked++

            for (removed in oldSuccessors.keys) {
                if (removed !in newSuccessors) {
                    updateEdge(node, removed, INF)
                    edgesRemoved++
                }
            }

            for ((successor, newCost) in newSuccessors) {
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
        require(!newCost.isNaN() && newCost >= 0.0) {
            "D* Lite edge costs must be non-negative or +infinity: $newCost"
        }
        // Read the old cost WITHOUT lazily initializing `from`: an external
        // provider (maneuver discovery) may have registered this edge in its
        // own maps already, and triggering successor generation here would
        // make oldCost == newCost — the rhs update below would then never
        // fire and the edge would stay invisible to the search forever.
        val oldCost = graph.knownSuccessors(from)[to] ?: INF
        if (sameCost(oldCost, newCost)) return
        graph.setCost(from, to, newCost)
        routeVersion++

        when {
            oldCost > newCost -> if (from != goal) setRhs(from, min(rhs(from), newCost + g(to)))
            sameCost(rhs(from), oldCost + g(to)) -> if (from != goal) setRhs(from, minSuccessorCost(from))
        }

        updateVertex(from)
    }

    fun path(maxLength: Int = 10_000): List<N> = routeCandidate(maxLength)?.nodes ?: emptyList()

    fun routeCandidate(maxLength: Int = 10_000): CoarseRouteCandidate<N>? {
        require(maxLength >= 0) { "maxLength must be non-negative" }
        if (start == goal) {
            return CoarseRouteCandidate(listOf(start), 0.0, exactFromStart = true, routeVersion = routeVersion)
        }
        if (rhs(start) == INF && g(start) == INF) return null

        val path = ArrayList<N>()
        val seen = HashSet<N>()
        var current = start
        var actualCost = 0.0

        path += current
        seen += current

        repeat(maxLength) {
            if (current == goal) {
                return CoarseRouteCandidate(path, actualCost, isStartCostExact(), routeVersion)
            }
            val successors = graph.successors(current)
            if (successors.isEmpty()) return null
            var next: N? = null
            var bestCost = INF
            for ((successor, edgeCost) in successors) {
                val candidateCost = edgeCost + g(successor)
                val better = candidateCost < bestCost ||
                    (candidateCost.compareTo(bestCost) == 0 && next != null &&
                        (nodeTieBreaker?.compare(successor, next) ?: 0) < 0)
                if (next == null || better) {
                    next = successor
                    bestCost = candidateCost
                }
            }
            next ?: return null
            val edgeCost = successors[next] ?: return null
            if (!edgeCost.isFinite() || !seen.add(next)) return null
            actualCost += edgeCost
            path += next
            current = next
        }

        return null
    }

    /** Exact only for the converged active start or the mathematical goal. */
    fun tailCost(node: N = start): TailCost {
        if (node == goal) return TailCost.Exact(0.0, routeVersion)
        val lower = checkedHeuristic(node, goal)
        if (node == start && isStartCostExact()) {
            val exact = g(start)
            return if (exact.isFinite()) TailCost.Exact(exact, routeVersion)
            else TailCost.Unreachable(routeVersion)
        }

        val upper = if (node == start) routeCandidate()?.ticks else null
        return TailCost.Bounds(lowerBound = lower, upperBound = upper, routeVersion = routeVersion)
    }

    fun isStartCostExact(): Boolean = start == goal || !shouldCompute() && sameCost(g(start), rhs(start))

    /** Internal search label; arbitrary nodes are not certified. Prefer [tailCost]. */
    fun g(node: N): Double = gValues[node] ?: INF

    /** Internal one-step lookahead label; not a public cost-to-go certificate. */
    fun rhs(node: N): Double = rhsValues[node] ?: INF

    fun key(node: N): Key = calculateKey(node)

    fun isQueued(node: N): Boolean = node in queue

    fun updateVertex(node: N) {
        val inQueue = node in queue
        val nodeG = g(node)
        val nodeRhs = rhs(node)
        val inconsistent = !sameCost(nodeG, nodeRhs)

        when {
            inconsistent && inQueue -> queue.update(node, calculateKey(node, nodeG, nodeRhs))
            inconsistent && !inQueue -> queue.insert(node, calculateKey(node, nodeG, nodeRhs))
            !inconsistent && inQueue -> queue.remove(node)
        }
    }

    private fun shouldCompute() = queue.topKey(Key.INFINITY) < calculateKey(start) || !sameCost(rhs(start), g(start))

    private fun calculateKey(node: N): Key {
        return calculateKey(node, g(node), rhs(node))
    }

    private fun calculateKey(node: N, nodeG: Double, nodeRhs: Double): Key {
        val minCost = min(nodeG, nodeRhs)
        return Key(minCost + checkedHeuristic(start, node) + km, minCost)
    }

    private fun minSuccessorCost(node: N): Double {
        var best = INF
        for ((successor, cost) in graph.successors(node)) {
            val candidate = cost + g(successor)
            if (candidate < best) best = candidate
        }
        return best
    }

    private fun setG(node: N, value: Double) {
        if (value == INF) gValues.remove(node) else gValues[node] = value
    }

    private fun setRhs(node: N, value: Double) {
        if (value == INF) rhsValues.remove(node) else rhsValues[node] = value
    }

    private fun checkedHeuristic(from: N, to: N): Double {
        val value = heuristic(from, to)
        require(value.isFinite() && value >= 0.0) {
            "D* Lite heuristic must be finite and non-negative: h($from, $to)=$value"
        }
        return value
    }

    data class ComputeResult(
        val processedNodes: Int,
        val timedOut: Boolean,
        val expansionLimitReached: Boolean,
        val converged: Boolean,
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
        private const val DEADLINE_CHECK_MASK = 0x0F

        private fun sameCost(a: Double, b: Double, tolerance: Double = EPSILON): Boolean = when {
            a.isInfinite() || b.isInfinite() -> a == b
            else -> abs(a - b) <= tolerance
        }
    }
}
