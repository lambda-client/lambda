package com.lambda.pathing.graph

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap
import it.unimi.dsi.fastutil.longs.LongArrayList
import it.unimi.dsi.fastutil.longs.LongIterable
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import kotlin.math.abs
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Admissible, consistent estimate between two packed nodes; must be finite and non-negative. */
fun interface LongHeuristic {
    fun estimate(from: Long, to: Long): Double
}

/**
 * D* Lite over packed long nodes: g/rhs in primitive maps, the frontier in a
 * [LongIndexedHeap] keyed by `(min(g, rhs) + h(start, node) + km, min(g, rhs))`.
 *
 * The algorithm is the generic `DStarLite<N>` it replaces (kept under
 * `src/test/kotlin/pathing/reference/` as the differential oracle), with the node
 * tie-break fixed to natural long order -- which for [com.lambda.pathing.coarse.PackedStance]
 * is exactly the old `compareBy(y, x, z, speed)`.
 *
 * One greedy descent ([descend]) serves route extraction, the route candidate and the
 * diagnostic report. The old diagnostic walker had no tie-break; the unified one uses it
 * everywhere, so the report can no longer disagree with the route it describes.
 */
class LongDStarLite(
    private val graph: LongLazyGraph,
    start: Long,
    val goal: Long,
    private val heuristic: LongHeuristic,
    private val describe: (Long) -> String = { it.toString() },
) {
    var start: Long = start
        private set

    private val gValues = Long2DoubleOpenHashMap().apply { defaultReturnValue(INF) }
    private val rhsValues = Long2DoubleOpenHashMap().apply { defaultReturnValue(INF) }

    val queue = LongIndexedHeap()

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
        queue.insert(goal, checkedHeuristic(start, goal), 0.0)
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
        var stall: String? = null
        // A live session once spent a million steps in 47 ms without converging: the loop
        // was re-processing one node whose values never settled. Detect the pattern and
        // report the node rather than burning the budget silently.
        var stallNode = 0L
        var stallG = 0.0
        var stallRhs = 0.0
        var stallSteps = 0

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
            if (queue.isEmpty()) {
                stall = "queue empty while start is inconsistent: start=${describe(start)} g=${g(start)} rhs=${rhs(start)}"
                break
            }
            val top = queue.top()
            val topG = g(top)
            val topRhs = rhs(top)
            if (top == stallNode && topG == stallG && topRhs == stallRhs) {
                if (++stallSteps >= STALL_STEPS) {
                    stall = "node ${describe(top)} re-processed $stallSteps times with g=$topG rhs=$topRhs key=(${queue.topFirst()}, ${queue.topSecond()}) km=$km"
                    break
                }
            } else {
                stallNode = top
                stallG = topG
                stallRhs = topRhs
                stallSteps = 0
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
            stall = stall,
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

        while (!queue.isEmpty() && queue.topFirst() <= threshold) {
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
        val oldFirst = queue.topFirst()
        val oldSecond = queue.topSecond()
        val node = queue.top()
        val nodeG = g(node)
        val nodeRhs = rhs(node)
        val minCost = min(nodeG, nodeRhs)
        val newFirst = minCost + checkedHeuristic(start, node) + km
        val newSecond = minCost

        when {
            LongIndexedHeap.compareKeys(oldFirst, oldSecond, newFirst, newSecond) < 0 ->
                queue.update(node, newFirst, newSecond)

            nodeG > nodeRhs -> {
                val predecessors = graph.predecessors(node)
                val settledG = nodeRhs
                setG(node, settledG)
                queue.remove(node)
                for (i in 0 until predecessors.size) {
                    val predecessor = predecessors.node(i)
                    val cost = predecessors.cost(i)
                    if (predecessor != goal) setRhs(predecessor, min(rhs(predecessor), cost + settledG))
                    updateVertex(predecessor)
                }
            }

            else -> {
                val predecessors = graph.predecessors(node)
                for (i in 0 until predecessors.size) graph.successors(predecessors.node(i))
                graph.successors(node)
                val oldG = nodeG
                setG(node, INF)

                for (i in 0 until predecessors.size) {
                    val predecessor = predecessors.node(i)
                    if (predecessor != goal && sameCost(
                            rhs(predecessor),
                            graph.knownSuccessors(predecessor).costOf(node) + oldG,
                        )
                    ) {
                        setRhs(predecessor, minSuccessorCost(predecessor))
                    }
                    updateVertex(predecessor)
                }

                if (node !in predecessors) {
                    if (node != goal && sameCost(
                            rhs(node),
                            graph.knownSuccessors(node).costOf(node) + oldG,
                        )
                    ) {
                        setRhs(node, minSuccessorCost(node))
                    }
                    updateVertex(node)
                }
            }
        }
    }

    fun updateStart(newStart: Long) {
        if (newStart == start) return
        val previousStart = start
        start = newStart
        km += checkedHeuristic(previousStart, newStart)

        if (newStart != goal) setRhs(newStart, minSuccessorCost(newStart))
        updateVertex(newStart)
        routeVersion++
    }

    /**
     * Regenerates the adjacency of every affected node from the providers and feeds each
     * difference through [updateEdge]. Nodes are visited in ascending long order -- a
     * canonical, JVM-independent sequence (the generic version iterated a hash set).
     */
    fun synchronizeAffected(affectedNodes: LongIterable): SynchronizationResult {
        var nodesChecked = 0
        var edgesAdded = 0
        var edgesRemoved = 0
        var edgesChanged = 0

        val unique = LongOpenHashSet()
        val iterator = affectedNodes.iterator()
        while (iterator.hasNext()) unique.add(iterator.nextLong())
        val ordered = unique.toLongArray()
        ordered.sort()

        for (node in ordered) {
            val knownView = graph.knownSuccessors(node)
            if (node !in graph && knownView.isEmpty()) continue

            val oldSuccessors = if (knownView.isEmpty()) EdgeList.EMPTY else knownView.copy()
            val newSuccessors = graph.generateSuccessors(node)
            graph.markSuccessorsInitialized(node)
            nodesChecked++

            for (i in 0 until oldSuccessors.size) {
                val removed = oldSuccessors.node(i)
                if (removed !in newSuccessors) {
                    updateEdge(node, removed, INF)
                    edgesRemoved++
                }
            }

            for (i in 0 until newSuccessors.size) {
                val successor = newSuccessors.node(i)
                val newCost = newSuccessors.cost(i)
                val oldIndex = oldSuccessors.indexOf(successor)
                when {
                    oldIndex < 0 -> {
                        updateEdge(node, successor, newCost)
                        edgesAdded++
                    }
                    !sameCost(oldSuccessors.cost(oldIndex), newCost) -> {
                        updateEdge(node, successor, newCost)
                        edgesChanged++
                    }
                }
            }

            val oldPredecessors = graph.knownPredecessors(node).copy()
            val newPredecessors = graph.generatePredecessors(node)
            graph.markPredecessorsInitialized(node)

            for (i in 0 until oldPredecessors.size) {
                val removed = oldPredecessors.node(i)
                if (removed !in newPredecessors) {
                    updateEdge(removed, node, INF)
                    edgesRemoved++
                }
            }

            for (i in 0 until newPredecessors.size) {
                val predecessor = newPredecessors.node(i)
                val newCost = newPredecessors.cost(i)
                val oldIndex = oldPredecessors.indexOf(predecessor)
                when {
                    oldIndex < 0 -> {
                        updateEdge(predecessor, node, newCost)
                        edgesAdded++
                    }
                    !sameCost(oldPredecessors.cost(oldIndex), newCost) -> {
                        updateEdge(predecessor, node, newCost)
                        edgesChanged++
                    }
                }
            }
        }

        return SynchronizationResult(nodesChecked, edgesAdded, edgesRemoved, edgesChanged)
    }

    fun updateEdge(from: Long, to: Long, newCost: Double) {
        require(!newCost.isNaN() && newCost >= 0.0) {
            "D* Lite edge costs must be non-negative or +infinity: $newCost"
        }

        val oldCost = graph.knownSuccessors(from).costOf(to)
        if (sameCost(oldCost, newCost)) return
        graph.setCost(from, to, newCost)
        routeVersion++

        when {
            oldCost > newCost -> if (from != goal) setRhs(from, min(rhs(from), newCost + g(to)))
            sameCost(rhs(from), oldCost + g(to)) -> if (from != goal) setRhs(from, minSuccessorCost(from))
        }

        updateVertex(from)
    }

    fun computeRouteCandidate(
        maxLength: Int = 10_000,
        timeBudget: Duration = Duration.INFINITE,
        maxExpansions: Int = Int.MAX_VALUE,
        cancelled: () -> Boolean = { false },
    ): LongRouteCandidate? {
        var budget = maxExpansions
        repeat(MAX_DESCENT_ROUNDS) {
            if (cancelled()) return null
            when (val outcome = descend(maxLength, strict = true, report = null)) {
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

    private sealed interface Descent {
        class Reached(val candidate: LongRouteCandidate) : Descent
        class Blocked(val node: Long) : Descent
        data object Unreachable : Descent
    }

    private fun settle(
        node: Long,
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

    private fun isSettled(node: Long): Boolean {
        if (!sameCost(g(node), rhs(node))) return false
        val minCost = min(g(node), rhs(node))
        return queue.topIsAbove(minCost + checkedHeuristic(start, node) + km, minCost)
    }

    /**
     * The one greedy walk from [start] along `argmin(edge + g(successor))`, ties to the
     * smaller node. [strict] is the route-extraction mode: an inconsistent node or an
     * infinite best cost blocks the walk so the caller can settle it; the lenient mode
     * (route candidate, diagnostics) walks on exactly as the old `routeCandidate` did.
     */
    private fun descend(maxLength: Int, strict: Boolean, report: StringBuilder?): Descent {
        if (start == goal) {
            report?.append("at-goal")
            return Descent.Reached(
                LongRouteCandidate(LongArrayList.of(start), 0.0, exactFromStart = true, routeVersion = routeVersion)
            )
        }
        if (rhs(start) == INF && g(start) == INF) {
            report?.append("unreachable-start")
            return Descent.Unreachable
        }

        val path = LongArrayList()
        val seen = LongOpenHashSet()
        var current = start
        var actualCost = 0.0

        path.add(current)
        seen.add(current)

        for (step in 0 until maxLength) {
            if (current == goal) {
                report?.append("reaches-goal-in-$step")
                return Descent.Reached(LongRouteCandidate(path, actualCost, isStartCostExact(), routeVersion))
            }

            if (strict && !sameCost(g(current), rhs(current))) {
                report?.append("inconsistent-at-${describe(current)}-after-$step")
                return Descent.Blocked(current)
            }

            val successors = graph.successors(current)
            if (successors.isEmpty()) {
                report?.append("no-successors-at-${describe(current)}-after-$step")
                return Descent.Unreachable
            }
            var next = 0L
            var bestCost = INF
            var bestEdge = INF
            var found = false
            for (i in 0 until successors.size) {
                val successor = successors.node(i)
                val edgeCost = successors.cost(i)
                val candidateCost = edgeCost + g(successor)
                val better = candidateCost < bestCost ||
                    (candidateCost.compareTo(bestCost) == 0 && found && successor < next)
                if (!found || better) {
                    next = successor
                    bestCost = candidateCost
                    bestEdge = edgeCost
                    found = true
                }
            }
            if (!bestEdge.isFinite()) {
                report?.append("infinite-edge-at-${describe(current)}-after-$step")
                return if (strict) Descent.Blocked(current) else Descent.Unreachable
            }
            if (strict && !bestCost.isFinite()) {
                report?.append("infinite-cost-at-${describe(current)}-after-$step")
                return Descent.Blocked(current)
            }

            if (!seen.add(next)) {
                report?.append(
                    "cycles-at-${describe(current)}-to-${describe(next)}-after-$step" +
                        " (g=%.1f -> %.1f)".format(g(current), g(next)),
                )
                return Descent.Blocked(next)
            }
            actualCost += bestEdge
            path.add(next)
            current = next
        }

        report?.append("exceeds-$maxLength-nodes")
        return Descent.Blocked(current)
    }

    fun routeCandidate(maxLength: Int = 10_000): LongRouteCandidate? {
        require(maxLength >= 0) { "maxLength must be non-negative" }
        return (descend(maxLength, strict = false, report = null) as? Descent.Reached)?.candidate
    }

    fun routeDescentReport(maxLength: Int = 10_000): String {
        val report = StringBuilder()
        descend(maxLength, strict = false, report = report)
        return report.toString()
    }

    fun tailCost(node: Long = start): TailCost {
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

    fun g(node: Long): Double = gValues.get(node)

    fun rhs(node: Long): Double = rhsValues.get(node)

    fun updateVertex(node: Long) {
        val inQueue = node in queue
        val nodeG = g(node)
        val nodeRhs = rhs(node)
        val inconsistent = !sameCost(nodeG, nodeRhs)

        when {
            inconsistent && inQueue -> {
                val minCost = min(nodeG, nodeRhs)
                queue.update(node, minCost + checkedHeuristic(start, node) + km, minCost)
            }
            inconsistent && !inQueue -> {
                val minCost = min(nodeG, nodeRhs)
                queue.insert(node, minCost + checkedHeuristic(start, node) + km, minCost)
            }
            !inconsistent && inQueue -> queue.remove(node)
        }
    }

    private fun shouldCompute(): Boolean {
        val startG = g(start)
        val startRhs = rhs(start)
        val minCost = min(startG, startRhs)
        return queue.topIsBelow(minCost + checkedHeuristic(start, start) + km, minCost) || !sameCost(startRhs, startG)
    }

    private fun minSuccessorCost(node: Long): Double {
        var best = INF
        val successors = graph.successors(node)
        for (i in 0 until successors.size) {
            val candidate = successors.cost(i) + g(successors.node(i))
            if (candidate < best) best = candidate
        }
        return best
    }

    private fun setG(node: Long, value: Double) {
        require(!value.isNaN()) { "D* Lite g must not be NaN: ${describe(node)}" }
        if (value == INF) gValues.remove(node) else gValues.put(node, value)
    }

    private fun setRhs(node: Long, value: Double) {
        require(!value.isNaN()) { "D* Lite rhs must not be NaN: ${describe(node)}" }
        if (value == INF) rhsValues.remove(node) else rhsValues.put(node, value)
    }

    private fun checkedHeuristic(from: Long, to: Long): Double {
        val value = heuristic.estimate(from, to)
        require(value.isFinite() && value >= 0.0) {
            "D* Lite heuristic must be finite and non-negative: h(${describe(from)}, ${describe(to)})=$value"
        }
        return value
    }

    data class ComputeResult(
        val processedNodes: Int,
        val timedOut: Boolean,
        val expansionLimitReached: Boolean,
        val cancelled: Boolean,
        val converged: Boolean,
        /** Non-null when the loop was cut short because one node never settled; names it. */
        val stall: String? = null,
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
        /** Consecutive steps on one unchanged top node before the loop is declared stalled. */
        private const val STALL_STEPS = 4096

        private const val MAX_DESCENT_ROUNDS = 256

        private fun sameCost(a: Double, b: Double, tolerance: Double = EPSILON): Boolean = when {
            a.isInfinite() || b.isInfinite() -> a == b
            else -> abs(a - b) <= tolerance
        }
    }
}
