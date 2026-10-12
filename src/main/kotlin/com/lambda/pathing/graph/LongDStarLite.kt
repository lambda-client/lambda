package com.lambda.pathing.graph

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap
import it.unimi.dsi.fastutil.longs.LongArrayList
import it.unimi.dsi.fastutil.longs.LongConsumer
import it.unimi.dsi.fastutil.longs.LongIterable
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import java.util.function.LongPredicate
import kotlin.math.abs
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

fun interface LongHeuristic {
	fun estimate(from: Long, to: Long): Double
}

class LongDStarLite(
	private val graph: LongGraph,
	start: Long,
	private val goal: Long,
	private val heuristic: LongHeuristic,
	private val describe: (Long) -> String = { it.toString() },
) {
	var start: Long = start
		private set

	private val nodes = NodeTable()

	private val heap = SlotHeap()

	private val successorScratch = EdgeList()

	private val predecessorScratch = EdgeList()

	private val goalSlot = nodes.slot(goal)

	val queue = Queue()

	var km = 0.0
		private set

	init {
		nodes.slot(start)
		setRhs(goalSlot, 0.0)
		heap.insert(goalSlot, checkedHeuristic(start, goal), 0.0)
	}

	inner class Queue {
		fun size(): Int = heap.size()

		fun isEmpty(): Boolean = heap.isEmpty()

		fun top(): Long = nodes.key(heap.top())

		fun topFirst(): Double = heap.topFirst()

		fun topSecond(): Double = heap.topSecond()

		operator fun contains(node: Long): Boolean {
			val slot = nodes.find(node)
			return slot >= 0 && slot in heap
		}
	}

	val knownNodeCount: Int get() = nodes.size

	fun isKnown(node: Long): Boolean = nodes.find(node) >= 0

	fun forEachKnownNode(action: LongConsumer) {
		for (slot in 0 until nodes.size) action.accept(nodes.key(slot))
	}

	fun knownNodes(): LongOpenHashSet = LongOpenHashSet().also { set -> forEachKnownNode { set.add(it) } }

	fun successors(node: Long): EdgeList = graph.successors(node)

	fun computeShortestPath(
		timeBudget: Duration = 500.milliseconds,
		maxExpansions: Int = Int.MAX_VALUE,
		cancelled: () -> Boolean = { false },
	): ComputeResult {
		require(!timeBudget.isNegative()) { "timeBudget must be non-negative" }
		require(maxExpansions >= 0) { "maxExpansions must be non-negative" }

		return compute(timeBudget, maxExpansions, cancelled, ::shouldCompute) {
			if (heap.isEmpty()) {
				return@compute "queue empty while start is inconsistent: start=${describe(start)} g=${g(start)} rhs=${rhs(start)}"
			}
			val top = heap.top()
			val topG = nodes.g(top)
			val topRhs = nodes.rhs(top)
			if (top == stallNode && topG == stallG && topRhs == stallRhs) {
				if (++stallSteps >= STALL_STEPS) {
					return@compute "node ${describe(nodes.key(top))} re-processed $stallSteps times with g=$topG rhs=$topRhs key=(${heap.topFirst()}, ${heap.topSecond()}) km=$km"
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

	private var stallNode = -1
	private var stallG = 0.0
	private var stallRhs = 0.0
	private var stallSteps = 0

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
			keepGoing = { !heap.isEmpty() && heap.topFirst() <= threshold },
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
		val node = heap.top()
		val nodeG = nodes.g(node)
		val nodeRhs = nodes.rhs(node)
		val minCost = min(nodeG, nodeRhs)
		val newFirst = firstKey(node, minCost)

		when {
			heap.topIsBelow(newFirst, minCost) -> heap.insert(node, newFirst, minCost)

			nodeG > nodeRhs -> {
				setG(node, nodeRhs)
				heap.remove(node)
				val predecessors = graph.predecessorsInto(nodes.key(node), predecessorScratch)
				for (i in 0 until predecessors.size) {
					val predecessor = nodes.slot(predecessors.node(i))
					if (predecessor != goalSlot) {
						val through = predecessors.cost(i) + nodeRhs
						if (through < nodes.rhs(predecessor)) nodes.setBest(predecessor, node)
						setRhs(predecessor, min(nodes.rhs(predecessor), through))
					}
					updateVertex(predecessor)
				}
			}

			else -> {
				setG(node, INF)
				val predecessors = graph.predecessorsInto(nodes.key(node), predecessorScratch)
				for (i in 0 until predecessors.size) {
					val predecessor = nodes.slot(predecessors.node(i))
					val best = nodes.best(predecessor)
					if (best == node || best == NodeTable.NONE) recompute(predecessor)
				}
				if (predecessors.indexOf(nodes.key(node)) < 0) recompute(node)
			}
		}
	}

	fun updateStart(newStart: Long) {
		if (newStart == start) return
		val previousStart = start
		start = newStart
		km += checkedHeuristic(previousStart, newStart)

		recompute(nodes.slot(newStart))
	}

	fun rekey() {
		for (slot in heap.slots()) {
			val minCost = min(nodes.g(slot), nodes.rhs(slot))
			heap.insert(slot, firstKey(slot, minCost), minCost)
		}
	}

	fun synchronizeAffected(affectedNodes: LongIterable): Boolean {
		val unique = LongOpenHashSet()
		val iterator = affectedNodes.iterator()
		while (iterator.hasNext()) unique.add(iterator.nextLong())
		val ordered = unique.toLongArray()
		ordered.sort()

		var changed = false
		for (node in ordered) {
			val slot = nodes.find(node)
			if (slot < 0) continue
			if (reconsider(slot)) changed = true
			val predecessors = graph.unknownPredecessorsInto(node, predecessorScratch, unknown).copy()
			for (i in 0 until predecessors.size) {
				val predecessor = predecessors.node(i)
				if (nodes.find(predecessor) < 0 && minSuccessorCost(predecessor).isFinite() && reconsider(nodes.slot(predecessor))) changed = true
			}
		}
		return changed
	}

	private fun reconsider(slot: Int): Boolean {
		val before = nodes.rhs(slot)
		recompute(slot)
		return !sameCost(before, nodes.rhs(slot))
	}

	fun refresh(node: Long) = recompute(nodes.slot(node))

	fun refreshIfReaching(node: Long) {
		val slot = nodes.find(node)
		if (slot >= 0) recompute(slot)
		else if (node != goal && hasSettledNodes && minSuccessorCost(node).isFinite()) recompute(nodes.slot(node))
	}

	fun edgeAdded(from: Long, to: Long, cost: Double) {
		val source = nodes.find(from)
		val target = nodes.find(to)
		if (source < 0 || target < 0 || source == goalSlot) return
		val through = cost + nodes.g(target)
		if (through < nodes.rhs(source)) {
			setRhs(source, through)
			nodes.setBest(source, target)
			updateVertex(source)
		}
	}

	fun edgeRemoved(from: Long, to: Long, cost: Double) {
		val source = nodes.find(from)
		val target = nodes.find(to)
		if (source < 0 || target < 0 || source == goalSlot) return
		if (sameCost(nodes.rhs(source), cost + nodes.g(target))) recompute(source)
	}

	private fun recompute(slot: Int) {
		if (slot != goalSlot) {
			setRhs(slot, minSuccessorCost(slot))
			nodes.setBest(slot, bestSuccessor)
		}
		updateVertex(slot)
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
		class Blocked(val node: Int) : Descent
		data object Unreachable : Descent
	}

	private fun settle(node: Int, maxExpansions: Int, cancelled: () -> Boolean): Int = compute(
		Duration.INFINITE, maxExpansions, cancelled,
		keepGoing = { !heap.isEmpty() && (shouldCompute() || !isSettled(node)) },
		stallReason = { null },
	).processedNodes

	private fun isSettled(node: Int): Boolean {
		if (!sameCost(nodes.g(node), nodes.rhs(node))) return false
		val minCost = min(nodes.g(node), nodes.rhs(node))
		return heap.topIsAbove(firstKey(node, minCost), minCost)
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
		var current = nodes.slot(start)
		var actualCost = 0.0

		path.add(start)
		seen.add(start)

		for (step in 0 until MAX_ROUTE_NODES) {
			if (current == goalSlot) {
				report?.append("reaches-goal-in-$step")
				return Descent.Reached(LongRouteCandidate(path, actualCost, isStartCostExact()))
			}

			if (strict && !sameCost(nodes.g(current), nodes.rhs(current))) {
				report?.append("inconsistent-at-${describe(nodes.key(current))}-after-$step")
				return Descent.Blocked(current)
			}

			val successors = graph.successorsInto(nodes.key(current), successorScratch)
			if (successors.isEmpty()) {
				report?.append("no-successors-at-${describe(nodes.key(current))}-after-$step")
				return Descent.Unreachable
			}
			var next = -1
			var bestEdge = INF
			var bestCost = INF
			for (i in 0 until successors.size) {
				val successor = nodes.slot(successors.node(i))
				val edgeCost = successors.cost(i)
				val candidateCost = edgeCost + nodes.g(successor)
				if (next < 0 || candidateCost < bestCost || (candidateCost.compareTo(bestCost) == 0 && nodes.key(successor) < nodes.key(next))) {
					next = successor
					bestCost = candidateCost
					bestEdge = edgeCost
				}
			}
			if (strict && !bestCost.isFinite()) {
				report?.append("infinite-cost-at-${describe(nodes.key(current))}-after-$step")
				return Descent.Blocked(current)
			}

			if (!seen.add(nodes.key(next))) {
				report?.append(
					"cycles-at-${describe(nodes.key(current))}-to-${describe(nodes.key(next))}-after-$step" +
							" (g=%.1f -> %.1f)".format(nodes.g(current), nodes.g(next)),
				)
				return Descent.Blocked(next)
			}
			actualCost += bestEdge
			path.add(nodes.key(next))
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

	fun heuristicToGoal(node: Long): Double = checkedHeuristic(node, goal)

	fun g(node: Long): Double {
		val slot = nodes.find(node)
		return if (slot < 0) INF else nodes.g(slot)
	}

	fun rhs(node: Long): Double {
		val slot = nodes.find(node)
		return if (slot < 0) INF else nodes.rhs(slot)
	}

	fun labels(include: LongPredicate): Long2DoubleOpenHashMap {
		val labels = Long2DoubleOpenHashMap().apply { defaultReturnValue(INF) }
		for (slot in 0 until nodes.size) {
			val label = min(nodes.g(slot), nodes.rhs(slot))
			if (label.isFinite() && include.test(nodes.key(slot))) labels.put(nodes.key(slot), label)
		}
		return labels
	}

	private fun updateVertex(node: Int) {
		val nodeG = nodes.g(node)
		val nodeRhs = nodes.rhs(node)
		if (sameCost(nodeG, nodeRhs)) {
			heap.remove(node)
			return
		}
		val minCost = min(nodeG, nodeRhs)
		heap.insert(node, firstKey(node, minCost), minCost)
	}

	private fun firstKey(node: Int, minCost: Double): Double = minCost + checkedHeuristic(start, nodes.key(node)) + km

	private fun shouldCompute(): Boolean {
		val startG = g(start)
		val startRhs = rhs(start)
		val minCost = min(startG, startRhs)
		val startKey = minCost + checkedHeuristic(start, start) + km
		return heap.topIsBelow(startKey, minCost) || !sameCost(startRhs, startG)
	}

	private fun minSuccessorCost(node: Int): Double = minSuccessorCost(nodes.key(node))

	private var bestSuccessor = NodeTable.NONE

	private val reached = LongPredicate { node -> nodes.find(node).let { it >= 0 && nodes.g(it) < INF } }

	private val unknown = LongPredicate { node -> nodes.find(node) < 0 }

	private fun minSuccessorCost(key: Long): Double {
		var best = INF
		bestSuccessor = NodeTable.NONE
		val successors = graph.reachedSuccessorsInto(key, successorScratch, reached)
		for (i in 0 until successors.size) {
			val target = nodes.find(successors.node(i))
			if (target < 0) continue
			val candidate = successors.cost(i) + nodes.g(target)
			if (candidate < best) {
				best = candidate
				bestSuccessor = target
			}
		}
		return best
	}

	var revision = 0L
		private set

	var hasSettledNodes = false
		private set

	private fun setG(node: Int, value: Double) {
		require(!value.isNaN()) { "D* Lite g must not be NaN: ${describe(nodes.key(node))}" }
		revision++
		if (value < INF) hasSettledNodes = true
		nodes.setG(node, value)
	}

	private fun setRhs(node: Int, value: Double) {
		require(!value.isNaN()) { "D* Lite rhs must not be NaN: ${describe(nodes.key(node))}" }
		revision++
		nodes.setRhs(node, value)
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
		val provenUnreachable: Boolean = false,
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
