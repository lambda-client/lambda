package com.lambda.pathing.graph

import kotlin.math.abs
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class DStarLite<N>(
    private val graph: LazyGraph<N>,
    start: N,
    val goal: N,
    private val heuristic: (N, N) -> Double,
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

    fun computeShortestPath(
        timeBudget: Duration = 500.milliseconds,
        maxExpansions: Int = Int.MAX_VALUE,
        cancelled: () -> Boolean = { false },
    ): ComputeResult {
        require(!timeBudget.isNegative()) { "timeBudget must be non-negative" }
        require(maxExpansions >= 0) { "maxExpansions must be non-negative" }

        val startedAt = System.nanoTime()
        val budgetNanos = timeBudget.inWholeNanoseconds
        var processed = 0
        var timedOut = false
        var expansionLimitReached = false
        var wasCancelled = false

        while (shouldCompute()) {
            if ((processed and DEADLINE_CHECK_MASK) == 0 && cancelled()) {
                wasCancelled = true
                break
            }
            if (processed >= maxExpansions) {
                expansionLimitReached = true
                break
            }

            if ((processed and DEADLINE_CHECK_MASK) == 0 && System.nanoTime() - startedAt >= budgetNanos) {
                timedOut = true
                break
            }

            step()
            processed++
        }

        return ComputeResult(
            processedNodes = processed,
            timedOut = timedOut,
            expansionLimitReached = expansionLimitReached,
            cancelled = wasCancelled,
            converged = !shouldCompute(),
        )
    }

    fun expandField(
        extraTicks: Double,
        timeBudget: Duration = 50.milliseconds,
        maxExpansions: Int = Int.MAX_VALUE,
        cancelled: () -> Boolean = { false },
    ): ComputeResult {
        require(extraTicks >= 0.0) { "extraTicks must be non-negative" }
        require(maxExpansions >= 0) { "maxExpansions must be non-negative" }

        val startedAt = System.nanoTime()
        val budgetNanos = timeBudget.inWholeNanoseconds
        var processed = 0
        var timedOut = false
        var expansionLimitReached = false
        var wasCancelled = false

        val startCost = g(start)
        if (!startCost.isFinite()) return ComputeResult(
            0, timedOut = false, expansionLimitReached = false,
            cancelled = cancelled(), converged = true,
        )

        val threshold = startCost + km + extraTicks

        while (!queue.isEmpty() && queue.topKey(Key.INFINITY).first <= threshold) {
            if ((processed and DEADLINE_CHECK_MASK) == 0 && cancelled()) {
                wasCancelled = true
                break
            }
            if (processed >= maxExpansions) {
                expansionLimitReached = true
                break
            }
            if ((processed and DEADLINE_CHECK_MASK) == 0 && System.nanoTime() - startedAt >= budgetNanos) {
                timedOut = true
                break
            }

            step()
            processed++
        }

        return ComputeResult(
            processedNodes = processed,
            timedOut = timedOut,
            expansionLimitReached = expansionLimitReached,
            cancelled = wasCancelled,
            converged = !shouldCompute(),
        )
    }

    private fun step() {
        run {
            val oldKey = queue.topKey(Key.INFINITY)
            val node = queue.top()
            val newKey = calculateKey(node)

            when {
                oldKey < newKey -> queue.update(node, newKey)

                g(node) > rhs(node) -> {

                    val predecessors = graph.predecessors(node)
                    val settledG = rhs(node)
                    setG(node, settledG)
                    queue.remove(node)
                    predecessors.forEach { (predecessor, cost) ->
                        if (predecessor != goal) setRhs(predecessor, min(rhs(predecessor), cost + settledG))
                        updateVertex(predecessor)
                    }
                }

                else -> {

                    val predecessors = graph.predecessors(node)
                    predecessors.keys.forEach(graph::successors)
                    graph.successors(node)
                    val oldG = g(node)
                    setG(node, INF)

                    for (predecessor in predecessors.keys) {
                        if (predecessor != goal && sameCost(
                                rhs(predecessor),
                                (graph.knownSuccessors(predecessor)[node] ?: INF) + oldG,
                            )
                        ) {
                            setRhs(predecessor, minSuccessorCost(predecessor))
                        }
                        updateVertex(predecessor)
                    }

                    if (node !in predecessors) {
                        if (node != goal && sameCost(
                                rhs(node),
                                (graph.knownSuccessors(node)[node] ?: INF) + oldG,
                            )
                        ) {
                            setRhs(node, minSuccessorCost(node))
                        }
                        updateVertex(node)
                    }
                }
            }
        }
    }

    fun updateStart(newStart: N) {
        if (newStart == start) return
        val previousStart = start
        start = newStart
        km += checkedHeuristic(previousStart, newStart)

        if (newStart != goal) setRhs(newStart, minSuccessorCost(newStart))
        updateVertex(newStart)
        routeVersion++
    }

    fun synchronizeAffected(affectedNodes: Iterable<N>): SynchronizationResult {
        var nodesChecked = 0
        var edgesAdded = 0
        var edgesRemoved = 0
        var edgesChanged = 0

        val uniqueNodes = affectedNodes.toHashSet()
        for (node in uniqueNodes) {
            val knownView = graph.knownSuccessors(node)
            if (node !in graph && knownView.isEmpty()) continue

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

            val oldPredecessors = HashMap(graph.knownPredecessors(node))
            val newPredecessors = graph.generatePredecessors(node)
            graph.markPredecessorsInitialized(node)

            for (removed in oldPredecessors.keys) {
                if (removed !in newPredecessors) {
                    updateEdge(removed, node, INF)
                    edgesRemoved++
                }
            }

            for ((predecessor, newCost) in newPredecessors) {
                val oldCost = oldPredecessors[predecessor]
                when {
                    oldCost == null -> {
                        updateEdge(predecessor, node, newCost)
                        edgesAdded++
                    }
                    !sameCost(oldCost, newCost) -> {
                        updateEdge(predecessor, node, newCost)
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

    fun computeRouteCandidate(
        maxLength: Int = 10_000,
        timeBudget: Duration = Duration.INFINITE,
        maxExpansions: Int = Int.MAX_VALUE,
        cancelled: () -> Boolean = { false },
    ): CoarseRouteCandidate<N>? {
        var budget = maxExpansions
        repeat(MAX_DESCENT_ROUNDS) {
            if (cancelled()) return null
            when (val outcome = descend(maxLength)) {
                is Descent.Reached -> return outcome.candidate
                Descent.Unreachable -> return null
                is Descent.Blocked -> {
                    if (budget <= 0) return null
                    val settled = settle(outcome.node, timeBudget, budget, cancelled)
                    if (settled == 0) return null
                    budget -= settled
                }
            }
        }
        return null
    }

    private sealed interface Descent<out N> {
        data class Reached<N>(val candidate: CoarseRouteCandidate<N>) : Descent<N>
        data class Blocked<N>(val node: N) : Descent<N>
        data object Unreachable : Descent<Nothing>
    }

    private fun settle(
        node: N,
        timeBudget: Duration,
        maxExpansions: Int,
        cancelled: () -> Boolean,
    ): Int {
        val startedAt = System.nanoTime()
        val budgetNanos = timeBudget.inWholeNanoseconds
        var processed = 0
        while (!queue.isEmpty() && processed < maxExpansions) {

            if (!shouldCompute() && isSettled(node)) break
            if ((processed and DEADLINE_CHECK_MASK) == 0) {
                if (cancelled()) break
                if (System.nanoTime() - startedAt >= budgetNanos) break
            }
            step()
            processed++
        }
        return processed
    }

    private fun isSettled(node: N): Boolean =
        sameCost(g(node), rhs(node)) && queue.topKey(Key.INFINITY) > calculateKey(node)

    private fun descend(maxLength: Int): Descent<N> {
        if (start == goal) {
            return Descent.Reached(
                CoarseRouteCandidate(listOf(start), 0.0, exactFromStart = true, routeVersion = routeVersion)
            )
        }
        if (rhs(start) == INF && g(start) == INF) return Descent.Unreachable

        val path = ArrayList<N>()
        val seen = HashSet<N>()
        var current = start
        var actualCost = 0.0

        path += current
        seen += current

        repeat(maxLength) {
            if (current == goal) {
                return Descent.Reached(CoarseRouteCandidate(path, actualCost, isStartCostExact(), routeVersion))
            }

            if (!sameCost(g(current), rhs(current))) return Descent.Blocked(current)

            val successors = graph.successors(current)
            if (successors.isEmpty()) return Descent.Unreachable
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
            val chosen = next ?: return Descent.Unreachable
            val edgeCost = successors[chosen] ?: return Descent.Unreachable
            if (!edgeCost.isFinite() || !bestCost.isFinite()) return Descent.Blocked(current)

            if (!seen.add(chosen)) return Descent.Blocked(chosen)
            actualCost += edgeCost
            path += chosen
            current = chosen
        }

        return Descent.Blocked(current)
    }

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

    fun routeDescentReport(maxLength: Int = 10_000): String {
        if (start == goal) return "at-goal"
        if (rhs(start) == INF && g(start) == INF) return "unreachable-start"

        val seen = HashSet<N>()
        var current = start
        seen += current
        repeat(maxLength) { step ->
            if (current == goal) return "reaches-goal-in-$step"
            val successors = graph.successors(current)
            if (successors.isEmpty()) return "no-successors-at-$current-after-$step"
            var next: N? = null
            var bestCost = INF
            for ((successor, edgeCost) in successors) {
                val candidateCost = edgeCost + g(successor)
                if (next == null || candidateCost < bestCost) {
                    next = successor
                    bestCost = candidateCost
                }
            }
            val chosen = next ?: return "no-successor-choice-at-$current-after-$step"
            val edgeCost = successors[chosen] ?: return "vanished-edge-at-$current-after-$step"
            if (!edgeCost.isFinite()) return "infinite-edge-at-$current-after-$step"
            if (!seen.add(chosen)) {
                return "cycles-at-$current-to-$chosen-after-$step" +
                    " (g=%.1f -> %.1f)".format(g(current), g(chosen))
            }
            current = chosen
        }
        return "exceeds-$maxLength-nodes"
    }

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

    fun g(node: N): Double = gValues[node] ?: INF

    fun rhs(node: N): Double = rhsValues[node] ?: INF

    fun key(node: N): Key = calculateKey(node)

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
        val cancelled: Boolean,
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

        private const val MAX_DESCENT_ROUNDS = 256

        private fun sameCost(a: Double, b: Double, tolerance: Double = EPSILON): Boolean = when {
            a.isInfinite() || b.isInfinite() -> a == b
            else -> abs(a - b) <= tolerance
        }
    }
}
