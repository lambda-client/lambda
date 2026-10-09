package com.lambda.pathing.graph

import com.lambda.pathing.graph.NodeTable.Companion.KNOWN
import com.lambda.pathing.graph.NodeTable.Companion.PREDECESSORS_INITIALIZED
import com.lambda.pathing.graph.NodeTable.Companion.SUCCESSORS_INITIALIZED
import com.lambda.pathing.graph.NodeTable.Companion.SUCCESSORS_SET
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

	private val generated = EdgeList()

	private val goalSlot = nodes.slot(goal)

	val queue = Queue()

	var km = 0.0
		private set

	init {
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

	val knownNodeCount: Int get() = nodes.knownCount

	fun isKnown(node: Long): Boolean {
		val slot = nodes.find(node)
		return slot >= 0 && nodes.has(slot, KNOWN)
	}

	fun forEachKnownNode(action: LongConsumer) {
		for (slot in 0 until nodes.size) if (nodes.has(slot, KNOWN)) action.accept(nodes.key(slot))
	}

	fun knownNodes(): LongOpenHashSet = LongOpenHashSet().also { set -> forEachKnownNode { set.add(it) } }

	fun knownSuccessors(node: Long): EdgeList {
		val slot = nodes.find(node)
		return if (slot < 0) EdgeList() else edgeList(nodes.successors(slot))
	}

	fun successors(node: Long): EdgeList = edgeList(successors(nodes.slot(node)))

	fun computeShortestPath(
		timeBudget: Duration = 500.milliseconds,
		maxExpansions: Int = Int.MAX_VALUE,
		cancelled: () -> Boolean = { false },
	): ComputeResult {
		require(!timeBudget.isNegative()) { "timeBudget must be non-negative" }
		require(maxExpansions >= 0) { "maxExpansions must be non-negative" }

		var stallNode = -1
		var stallG = 0.0
		var stallRhs = 0.0
		var stallSteps = 0

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
				val predecessors = predecessors(node)
				setG(node, nodeRhs)
				heap.remove(node)
				for (i in 0 until predecessors.size) {
					val predecessor = predecessors.slot(i)
					val cost = predecessors.cost(i)
					if (predecessor != goalSlot) setRhs(predecessor, min(nodes.rhs(predecessor), cost + nodeRhs))
					updateVertex(predecessor)
				}
			}

			else -> {
				val predecessors = predecessors(node)
				for (i in 0 until predecessors.size) successors(predecessors.slot(i))
				successors(node)
				setG(node, INF)

				fun refresh(vertex: Int) {
					if (vertex != goalSlot && sameCost(nodes.rhs(vertex), costOf(vertex, node) + nodeG)) {
						setRhs(vertex, minSuccessorCost(vertex))
					}
					updateVertex(vertex)
				}
				for (i in 0 until predecessors.size) refresh(predecessors.slot(i))
				if (predecessors.indexOf(node) < 0) refresh(node)
			}
		}
	}

	fun updateStart(newStart: Long) {
		if (newStart == start) return
		val previousStart = start
		start = newStart
		km += checkedHeuristic(previousStart, newStart)

		val slot = nodes.slot(newStart)
		if (slot != goalSlot) setRhs(slot, minSuccessorCost(slot))
		updateVertex(slot)
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
			if (slot < 0 || !nodes.has(slot, KNOWN)) continue
			val oldSuccessors = edgeList(nodes.successors(slot))
			val newSuccessors = graph.successors(node)
			nodes.mark(slot, SUCCESSORS_INITIALIZED or KNOWN)
			if (reconcileEdges(oldSuccessors, newSuccessors) { target, cost -> updateEdge(node, target, cost) }) changed = true

			val oldPredecessors = edgeList(nodes.predecessors(slot))
			val newPredecessors = graph.predecessors(node)
			nodes.mark(slot, PREDECESSORS_INITIALIZED or KNOWN)
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
		val oldCost = knownCost(from, to)
		if (sameCost(oldCost, newCost)) return
		val fromSlot = nodes.slot(from)
		val toSlot = nodes.slot(to)
		if (newCost.isFinite()) putEdge(fromSlot, toSlot, newCost) else removeEdge(fromSlot, toSlot)

		when {
			oldCost > newCost -> if (fromSlot != goalSlot) setRhs(fromSlot, min(nodes.rhs(fromSlot), newCost + nodes.g(toSlot)))
			sameCost(nodes.rhs(fromSlot), oldCost + nodes.g(toSlot)) -> if (fromSlot != goalSlot) setRhs(fromSlot, minSuccessorCost(fromSlot))
		}

		updateVertex(fromSlot)
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

			val successors = successors(current)
			if (successors.isEmpty()) {
				report?.append("no-successors-at-${describe(nodes.key(current))}-after-$step")
				return Descent.Unreachable
			}
			var next = successors.slot(0)
			var bestEdge = successors.cost(0)
			var bestCost = bestEdge + nodes.g(next)
			for (i in 1 until successors.size) {
				val successor = successors.slot(i)
				val edgeCost = successors.cost(i)
				val candidateCost = edgeCost + nodes.g(successor)
				if (candidateCost < bestCost || (candidateCost.compareTo(bestCost) == 0 && nodes.key(successor) < nodes.key(next))) {
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

	private fun minSuccessorCost(node: Int): Double {
		var best = INF
		val successors = successors(node)
		for (i in 0 until successors.size) {
			val candidate = successors.cost(i) + nodes.g(successors.slot(i))
			if (candidate < best) best = candidate
		}
		return best
	}

	private fun costOf(from: Int, to: Int): Double = nodes.successors(from)?.costOf(to) ?: INF

	private fun knownCost(from: Long, to: Long): Double {
		val fromSlot = nodes.find(from)
		val toSlot = nodes.find(to)
		return if (fromSlot < 0 || toSlot < 0) INF else costOf(fromSlot, toSlot)
	}

	private fun successors(node: Int): SlotEdges {
		if (!nodes.has(node, SUCCESSORS_INITIALIZED)) {
			val edges = graph.successorsInto(nodes.key(node), generated)
			for (i in 0 until edges.size) addUnknownEdge(node, nodes.slot(edges.node(i)), edges.cost(i), mayExist = true)
			nodes.mark(node, SUCCESSORS_INITIALIZED or KNOWN)
		}
		return nodes.successors(node) ?: EMPTY
	}

	private fun predecessors(node: Int): SlotEdges {
		if (!nodes.has(node, PREDECESSORS_INITIALIZED)) {
			val edges = graph.predecessorsInto(nodes.key(node), generated)
			for (i in 0 until edges.size) {
				val from = nodes.slot(edges.node(i))
				addUnknownEdge(from, node, edges.cost(i), mayExist = nodes.has(from, SUCCESSORS_INITIALIZED or SUCCESSORS_SET))
			}
			nodes.mark(node, PREDECESSORS_INITIALIZED or KNOWN)
		}
		return nodes.predecessors(node) ?: EMPTY
	}

	private fun addUnknownEdge(from: Int, to: Int, cost: Double, mayExist: Boolean) {
		val successors = nodes.successorsOrCreate(from)
		if (mayExist && successors.indexOf(to) >= 0) return
		successors.append(to, cost)
		nodes.mark(from, KNOWN)
		nodes.mark(to, KNOWN)
		nodes.predecessorsOrCreate(to).append(from, cost)
	}

	private fun putEdge(from: Int, to: Int, cost: Double) {
		nodes.mark(from, KNOWN or SUCCESSORS_SET)
		nodes.mark(to, KNOWN)
		nodes.successorsOrCreate(from).put(to, cost)
		nodes.predecessorsOrCreate(to).put(from, cost)
	}

	private fun removeEdge(from: Int, to: Int) {
		nodes.successors(from)?.let { if (it.remove(to) && it.isEmpty()) nodes.dropSuccessors(from) }
		nodes.predecessors(to)?.let { if (it.remove(from) && it.isEmpty()) nodes.dropPredecessors(to) }
		refreshKnownNode(from)
		refreshKnownNode(to)
	}

	private fun refreshKnownNode(node: Int) {
		if (nodes.successors(node) == null && nodes.predecessors(node) == null &&
			!nodes.has(node, SUCCESSORS_INITIALIZED or PREDECESSORS_INITIALIZED)
		) {
			nodes.forget(node)
		}
	}

	private fun edgeList(edges: SlotEdges?): EdgeList {
		val out = EdgeList(maxOf(edges?.size ?: 0, 1))
		if (edges != null) for (i in 0 until edges.size) out.add(nodes.key(edges.slot(i)), edges.cost(i))
		return out
	}

	var revision = 0L
		private set

	private fun setG(node: Int, value: Double) {
		require(!value.isNaN()) { "D* Lite g must not be NaN: ${describe(nodes.key(node))}" }
		revision++
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
	)

	companion object {
		private const val INF = Double.POSITIVE_INFINITY
		private const val EPSILON = 1e-9
		private const val DEADLINE_CHECK_MASK = 0x0F

		private const val STALL_STEPS = 4096

		private const val MAX_DESCENT_ROUNDS = 256

		private const val MAX_ROUTE_NODES = 10_000

		private val EMPTY = SlotEdges()

		private fun sameCost(a: Double, b: Double, tolerance: Double = EPSILON): Boolean = when {
			a.isInfinite() || b.isInfinite() -> a == b
			else -> abs(a - b) <= tolerance
		}
	}
}
