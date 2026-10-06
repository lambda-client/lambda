package com.lambda.pathing.graph

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap
import it.unimi.dsi.fastutil.longs.LongArrayList
import it.unimi.dsi.fastutil.longs.LongIterable
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import kotlin.math.abs
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

fun interface LongHeuristic {
	fun estimate(from: Long, to: Long): Double
}

class LongDStarLite(
	private val graph: LongLazyGraph,
	start: Long,
	private val goal: Long,
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

	init {
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

		var stallNode = 0L
		var stallG = 0.0
		var stallRhs = 0.0
		var stallSteps = 0

		return compute(timeBudget, maxExpansions, cancelled, ::shouldCompute) {
			if (queue.isEmpty()) {
				return@compute "queue empty while start is inconsistent: start=${describe(start)} g=${g(start)} rhs=${rhs(start)}"
			}
			val top = queue.top()
			val topG = g(top)
			val topRhs = rhs(top)
			if (top == stallNode && topG == stallG && topRhs == stallRhs) {
				if (++stallSteps >= STALL_STEPS) {
					return@compute "node ${describe(top)} re-processed $stallSteps times with g=$topG rhs=$topRhs key=(${queue.topFirst()}, ${queue.topSecond()}) km=$km"
				}
			} else {
				stallNode = top
				stallG = topG
				stallRhs = topRhs
				stallSteps = 0
			}
			null
		}
	}

	fun expandField(
		extraTicks: Double,
		timeBudget: Duration = 50.milliseconds,
		maxExpansions: Int = Int.MAX_VALUE,
		cancelled: () -> Boolean = { false },
	): ComputeResult {
		require(extraTicks >= 0.0) { "extraTicks must be non-negative" }
		require(maxExpansions >= 0) { "maxExpansions must be non-negative" }

		val startCost = g(start)
		if (!startCost.isFinite()) return ComputeResult(
			0, timedOut = false, expansionLimitReached = false,
			cancelled = cancelled(), converged = true,
		)

		val threshold = startCost + km + extraTicks
		return compute(
			timeBudget, maxExpansions, cancelled,
			keepGoing = { !queue.isEmpty() && queue.topFirst() <= threshold },
			stallReason = { null },
		)
	}

	private inline fun compute(
		timeBudget: Duration,
		maxExpansions: Int,
		cancelled: () -> Boolean,
		keepGoing: () -> Boolean,
		stallReason: () -> String?,
	): ComputeResult {
		val startedAt = System.nanoTime()
		val budgetNanos = timeBudget.inWholeNanoseconds
		var processed = 0
		var timedOut = false
		var expansionLimitReached = false
		var wasCancelled = false
		var stall: String? = null
		while (keepGoing()) {
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
			stall = stallReason()
			if (stall != null) break
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

	private fun step() {
		val node = queue.top()
		val nodeG = g(node)
		val nodeRhs = rhs(node)
		val minCost = min(nodeG, nodeRhs)
		val newFirst = firstKey(node, minCost)

		when {
			queue.topIsBelow(newFirst, minCost) -> queue.insert(node, newFirst, minCost)

			nodeG > nodeRhs -> {
				val predecessors = graph.predecessors(node)
				setG(node, nodeRhs)
				queue.remove(node)
				for (i in 0 until predecessors.size) {
					val predecessor = predecessors.node(i)
					val cost = predecessors.cost(i)
					if (predecessor != goal) setRhs(predecessor, min(rhs(predecessor), cost + nodeRhs))
					updateVertex(predecessor)
				}
			}

			else -> {
				val predecessors = graph.predecessors(node)
				for (i in 0 until predecessors.size) graph.successors(predecessors.node(i))
				graph.successors(node)
				setG(node, INF)

				fun refresh(vertex: Long) {
					if (vertex != goal && sameCost(rhs(vertex), graph.knownSuccessors(vertex).costOf(node) + nodeG)) {
						setRhs(vertex, minSuccessorCost(vertex))
					}
					updateVertex(vertex)
				}
				for (i in 0 until predecessors.size) refresh(predecessors.node(i))
				if (node !in predecessors) refresh(node)
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
	}

	fun synchronizeAffected(affectedNodes: LongIterable): Boolean {
		val unique = LongOpenHashSet()
		val iterator = affectedNodes.iterator()
		while (iterator.hasNext()) unique.add(iterator.nextLong())
		val ordered = unique.toLongArray()
		ordered.sort()

		var changed = false
		for (node in ordered) {
			if (node !in graph) continue
			val oldSuccessors = graph.knownSuccessors(node).copy()
			val newSuccessors = graph.generateSuccessors(node)
			graph.markSuccessorsInitialized(node)
			if (reconcileEdges(oldSuccessors, newSuccessors) { target, cost -> updateEdge(node, target, cost) }) changed = true

			val oldPredecessors = graph.knownPredecessors(node).copy()
			val newPredecessors = graph.generatePredecessors(node)
			graph.markPredecessorsInitialized(node)
			if (reconcileEdges(oldPredecessors, newPredecessors) { source, cost -> updateEdge(source, node, cost) }) changed = true
		}
		return changed
	}

	private inline fun reconcileEdges(old: EdgeList, new: EdgeList, apply: (Long, Double) -> Unit): Boolean {
		var changed = false
		for (i in 0 until old.size) {
			val node = old.node(i)
			if (node in new) continue
			apply(node, INF)
			changed = true
		}
		for (i in 0 until new.size) {
			val node = new.node(i)
			val cost = new.cost(i)
			val oldIndex = old.indexOf(node)
			if (oldIndex >= 0 && sameCost(old.cost(oldIndex), cost)) continue
			apply(node, cost)
			changed = true
		}
		return changed
	}

	fun updateEdge(from: Long, to: Long, newCost: Double) {
		EdgeList.checkCost(newCost)
		val oldCost = graph.knownSuccessors(from).costOf(to)
		if (sameCost(oldCost, newCost)) return
		graph.setCost(from, to, newCost)

		when {
			oldCost > newCost -> if (from != goal) setRhs(from, min(rhs(from), newCost + g(to)))
			sameCost(rhs(from), oldCost + g(to)) -> if (from != goal) setRhs(from, minSuccessorCost(from))
		}

		updateVertex(from)
	}

	fun computeRouteCandidate(
		maxExpansions: Int = Int.MAX_VALUE,
		cancelled: () -> Boolean = { false },
	): LongRouteCandidate? {
		var budget = maxExpansions
		repeat(MAX_DESCENT_ROUNDS) {
			if (cancelled()) return null
			when (val outcome = descend(strict = true, report = null)) {
				is Descent.Reached -> return outcome.candidate
				Descent.Unreachable -> return null
				is Descent.Blocked -> {
					if (budget <= 0) return null
					val settled = settle(outcome.node, budget, cancelled)
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

	private fun settle(node: Long, maxExpansions: Int, cancelled: () -> Boolean): Int = compute(
		Duration.INFINITE, maxExpansions, cancelled,
		keepGoing = { !queue.isEmpty() && (shouldCompute() || !isSettled(node)) },
		stallReason = { null },
	).processedNodes

	private fun isSettled(node: Long): Boolean {
		if (!sameCost(g(node), rhs(node))) return false
		val minCost = min(g(node), rhs(node))
		return queue.topIsAbove(firstKey(node, minCost), minCost)
	}

	private fun descend(strict: Boolean, report: StringBuilder?): Descent {
		if (start == goal) {
			report?.append("at-goal")
			return Descent.Reached(
				LongRouteCandidate(LongArrayList.of(start), 0.0, exactFromStart = true)
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

		for (step in 0 until MAX_ROUTE_NODES) {
			if (current == goal) {
				report?.append("reaches-goal-in-$step")
				return Descent.Reached(LongRouteCandidate(path, actualCost, isStartCostExact()))
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
			var next = successors.node(0)
			var bestEdge = successors.cost(0)
			var bestCost = bestEdge + g(next)
			for (i in 1 until successors.size) {
				val successor = successors.node(i)
				val edgeCost = successors.cost(i)
				val candidateCost = edgeCost + g(successor)
				if (candidateCost < bestCost || (candidateCost.compareTo(bestCost) == 0 && successor < next)) {
					next = successor
					bestCost = candidateCost
					bestEdge = edgeCost
				}
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

		report?.append("exceeds-$MAX_ROUTE_NODES-nodes")
		return Descent.Blocked(current)
	}

	fun routeCandidate(): LongRouteCandidate? =
		(descend(strict = false, report = null) as? Descent.Reached)?.candidate

	fun routeDescentReport(): String {
		val report = StringBuilder()
		descend(strict = false, report = report)
		return report.toString()
	}

	fun tailCost(node: Long = start): TailCost {
		if (node == goal) return TailCost.Exact(0.0)
		val lower = checkedHeuristic(node, goal)
		if (node == start && isStartCostExact()) {
			val exact = g(start)
			return if (exact.isFinite()) TailCost.Exact(exact)
			else TailCost.Unreachable
		}

		val upper = if (node == start) routeCandidate()?.ticks else null
		return TailCost.Bounds(lowerBound = lower, upperBound = upper)
	}

	fun isStartCostExact(): Boolean = start == goal || !shouldCompute() && sameCost(g(start), rhs(start))

	fun g(node: Long): Double = gValues.get(node)

	fun rhs(node: Long): Double = rhsValues.get(node)

	private fun updateVertex(node: Long) {
		val nodeG = g(node)
		val nodeRhs = rhs(node)
		if (sameCost(nodeG, nodeRhs)) {
			queue.remove(node)
			return
		}
		val minCost = min(nodeG, nodeRhs)
		queue.insert(node, firstKey(node, minCost), minCost)
	}

	private fun firstKey(node: Long, minCost: Double): Double = minCost + checkedHeuristic(start, node) + km

	private fun shouldCompute(): Boolean {
		val startG = g(start)
		val startRhs = rhs(start)
		val minCost = min(startG, startRhs)
		return queue.topIsBelow(firstKey(start, minCost), minCost) || !sameCost(startRhs, startG)
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
		val stall: String? = null,
	)

	companion object {
		private const val INF = Double.POSITIVE_INFINITY
		private const val EPSILON = 1e-9
		private const val DEADLINE_CHECK_MASK = 0x0F

		private const val STALL_STEPS = 4096

		private const val MAX_DESCENT_ROUNDS = 256

		private const val MAX_ROUTE_NODES = 10_000

		private fun sameCost(a: Double, b: Double, tolerance: Double = EPSILON): Boolean = when {
			a.isInfinite() || b.isInfinite() -> a == b
			else -> abs(a - b) <= tolerance
		}
	}
}
