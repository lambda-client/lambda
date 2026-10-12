package com.lambda.pathing.coarse

import com.lambda.pathing.actions.CoarseEdge
import com.lambda.pathing.core.CellBox
import com.lambda.pathing.core.PathingChunk
import com.lambda.pathing.core.Stance
import com.lambda.pathing.graph.CoarseRouteCandidate
import com.lambda.pathing.graph.EdgeList
import com.lambda.pathing.graph.LongDStarLite
import com.lambda.pathing.graph.LongGraph
import com.lambda.pathing.world.CoarseVoxelView
import it.unimi.dsi.fastutil.longs.LongArrayList
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class CoarsePlanner(
	val view: CoarseVoxelView,
	val moves: SimpleMoveLibrary,
	start: Stance,
	val goalStance: Stance,
) {
	private val goalNode: Long = PackedStance.pack(goalStance, SpeedClass.STOPPED)

	private val edgeCache = CoarseEdgeCache(view.cursor(), moves)

	private val momentum = MomentumRules(moves.templates)

	private val anchors = FrontierIndex(view, moves, goalStance, ::updateAnchorEdges)

	private var goalKnown = FrontierAnchors.goalResolved(view, goalStance)

	private fun anchorSuccessors(node: Long, sink: EdgeList) {
		val cost = anchors.cost(node)
		if (cost.isNaN()) return
		if (!goalKnown) sink.add(goalNode, cost)
		else {
			val from = PackedStance.stanceKey(node)
			anchors.forEachKey { to -> if (to != from) sink.append(PackedStance.withSpeed(to, SpeedClass.MOVING), transitCost(from, to)) }
		}
	}

	private val graph = LongGraph(
		successorProvider = { node, sink ->
			momentum.successors(edgeCache, node, sink)
			anchorSuccessors(node, sink)
		},
		reachedSuccessorProvider = { node, sink, reached ->
			momentum.successors(edgeCache, node, sink, reached = reached)
			anchorSuccessors(node, sink)
		},
		predecessorProvider = { node, sink ->
			momentum.predecessors(edgeCache, node, sink)
			anchorPredecessors(node, sink)
		},
		unknownPredecessorProvider = { node, sink, unknown ->
			momentum.predecessors(edgeCache, node, sink, unknown)
			anchorPredecessors(node, sink)
		},
	)

	private fun anchorPredecessors(node: Long, sink: EdgeList) {
		if (!goalKnown) {
			if (node == goalNode) anchors.forEach { stance, cost ->
				sink.add(PackedStance.pack(stance, SpeedClass.MOVING), cost)
				sink.add(PackedStance.pack(stance, SpeedClass.STOPPED), cost)
			}
		} else if (PackedStance.speed(node) == SpeedClass.MOVING && !anchors.cost(node).isNaN()) {
			val to = PackedStance.stanceKey(node)
			anchors.forEachKey { from ->
				if (from != to) {
					val cost = transitCost(from, to)
					sink.append(PackedStance.withSpeed(from, SpeedClass.MOVING), cost)
					sink.append(from, cost)
				}
			}
		}
	}

	private fun transitCost(from: Long, to: Long): Double =
		anchors.transitCost(from, to, momentum.heuristic(from, to, grounded = true))

	private var grounded = !view.mayBounce

	private val search = LongDStarLite(
		graph = graph,
		start = PackedStance.pack(start, SpeedClass.STOPPED),
		goal = goalNode,
		heuristic = { a, b -> momentum.heuristic(a, b, grounded && b != goalNode) },
		describe = PackedStance::describe,
	)

	init {
		anchors.rebuild()
	}

	val graphSize: Int get() = search.knownNodeCount

	fun forEachGraphStance(action: (x: Int, y: Int, z: Int) -> Unit) {
		search.forEachKnownNode { node ->
			if (PackedStance.speed(node) != SpeedClass.MOVING || !search.isKnown(PackedStance.withSpeed(node, SpeedClass.STOPPED))) {
				action(PackedStance.unpackX(node), PackedStance.unpackY(node), PackedStance.unpackZ(node))
			}
		}
	}

	fun forEachGraphStanceWithin(box: CellBox, action: (x: Int, y: Int, z: Int) -> Unit) {
		search.forEachKnownNode { node ->
			val x = PackedStance.unpackX(node)
			val y = PackedStance.unpackY(node)
			val z = PackedStance.unpackZ(node)
			if (box.contains(x, y, z) && (PackedStance.speed(node) != SpeedClass.MOVING || !search.isKnown(PackedStance.withSpeed(node, SpeedClass.STOPPED)))) action(x, y, z)
		}
	}

	fun stanceCost(stance: Stance): Double = minOf(
		search.g(PackedStance.pack(stance, SpeedClass.MOVING)),
		search.g(PackedStance.pack(stance, SpeedClass.STOPPED)),
	)

	fun inFrontier(stance: Stance): Boolean =
		PackedStance.pack(stance, SpeedClass.MOVING) in search.queue ||
				PackedStance.pack(stance, SpeedClass.STOPPED) in search.queue

	fun knownEdgesOf(node: Stance): List<CoarseEdge> = edgeCache.cachedEdgesFrom(node) ?: emptyList()

	val exactEdgeChecks: Long get() = edgeCache.exactChecks

	val arrivalEdgeChecks: Long get() = edgeCache.arrivalChecks

	var syncedStances = 0L
		private set

	fun knownSuccessorsOf(node: Stance): Map<Stance, Double> {
		val merged = HashMap<Stance, Double>()
		for (speed in SpeedClass.entries) {
			val edges = EdgeList().also { momentum.successors(edgeCache, PackedStance.pack(node, speed), it, settledOnly = true) }
			for (i in 0 until edges.size) {
				val successor = edges.node(i)
				val stance = PackedStance.stance(successor)
				if (stance == node) continue
				merged.merge(stance, edges.cost(i), ::minOf)
			}
		}
		return merged
	}

	fun repair(
		maxExpansions: Int = Int.MAX_VALUE,
		cancelled: () -> Boolean = { false },
	): LongDStarLite.ComputeResult {
		unreachableReport = null
		var processed = 0
		var closure: ForwardClosure? = null
		while (true) {
			val slice = minOf(REPAIR_SLICE, maxExpansions - processed)
			val result = search.computeShortestPath(Duration.INFINITE, slice, cancelled)
			processed += result.processedNodes
			if (!result.expansionLimitReached || processed >= maxExpansions) return result.copy(processedNodes = processed)
			val proof = closure ?: ForwardClosure(search.start).also { closure = it }
			if (proof.exhausted(REPAIR_SLICE / FORWARD_SHARE)) {
				unreachableReport = "the start's forward closure of ${proof.size} nodes reaches neither the goal nor the frontier"
				return result.copy(processedNodes = processed, provenUnreachable = true)
			}
		}
	}

	var unreachableReport: String? = null
		private set

	private inner class ForwardClosure(root: Long) {
		private val seen = LongOpenHashSet().apply { add(root) }
		private val open = it.unimi.dsi.fastutil.longs.LongHeapPriorityQueue { a, b ->
			search.heuristicToGoal(a).compareTo(search.heuristicToGoal(b))
		}.apply { enqueue(root) }
		private var reaching = false
		private val scratch = EdgeList()

		val size: Int get() = seen.size

		fun exhausted(budget: Int): Boolean {
			if (reaching) return false
			repeat(budget) {
				if (open.isEmpty) return true
				val node = open.dequeueLong()
				if (search.rhs(node).isFinite()) {
					reaching = true
					return false
				}
				scratch.clear()
				graph.successorsInto(node, scratch)
				for (i in 0 until scratch.size) {
					val next = scratch.node(i)
					if (next == goalNode) {
						reaching = true
						return false
					}
					if (seen.add(next)) open.enqueue(next)
				}
			}
			return false
		}
	}

	fun updateStart(start: Stance, speed: SpeedClass = SpeedClass.STOPPED) = search.updateStart(PackedStance.pack(start, speed))

	val anchorCount: Int get() = anchors.size

	val optimisticAnchors: Map<Stance, Double> get() = HashMap<Stance, Double>().also { copy -> anchors.forEach { stance, cost -> copy[stance] = cost } }

	fun isAnchor(stance: Stance): Boolean = stance in anchors

	private fun updateAnchorEdges(anchor: Stance, added: Boolean) {
		if (!search.hasSettledNodes) return
		val moving = PackedStance.pack(anchor, SpeedClass.MOVING)
		val stopped = PackedStance.pack(anchor, SpeedClass.STOPPED)
		if (goalKnown) {
			anchors.forEachKey { from ->
				if (from == stopped) return@forEachKey
				val cost = transitCost(from, stopped)
				for (source in longArrayOf(PackedStance.withSpeed(from, SpeedClass.MOVING), from)) {
					if (added) search.edgeAdded(source, moving, cost) else search.edgeRemoved(source, moving, cost)
				}
			}
		}
		search.refreshIfReaching(moving)
		search.refreshIfReaching(stopped)
	}

	private fun updateGoalKnowledge() {
		val known = FrontierAnchors.goalResolved(view, goalStance)
		if (known == goalKnown) return
		goalKnown = known
		anchors.forEachKey { key ->
			search.refreshIfReaching(PackedStance.withSpeed(key, SpeedClass.MOVING))
			search.refreshIfReaching(key)
		}
	}

	fun expandField(
		extraTicks: Double,
		timeBudget: Duration = 50.milliseconds,
		maxExpansions: Int = Int.MAX_VALUE,
		cancelled: () -> Boolean = { false },
	): LongDStarLite.ComputeResult = search.expandField(extraTicks, timeBudget, maxExpansions, cancelled)

	fun expandSearchField(timeBudget: Duration = Duration.INFINITE, cancelled: () -> Boolean = { false }) =
		expandField(SEARCH_FIELD_TICKS, timeBudget, SEARCH_FIELD_EXPANSIONS, cancelled)

	fun route(cancelled: () -> Boolean = { false }): CoarseRouteCandidate<Stance>? {
		val candidate = search.computeRouteCandidate(
			maxExpansions = ROUTE_DESCENT_EXPANSIONS,
			cancelled = cancelled,
		) ?: return null

		val nodes = candidate.nodes
		val stances = ArrayList<Stance>(nodes.size)
		var previous = 0L
		for (i in nodes.indices) {
			val node = nodes.getLong(i)
			if (i > 0 && !isRealEdge(previous, node)) break
			if (i == 0 || !PackedStance.sameStance(previous, node)) stances += PackedStance.stance(node)
			previous = node
		}
		return CoarseRouteCandidate(
			nodes = stances,
			ticks = candidate.ticks,
			exactFromStart = candidate.exactFromStart,
		)
	}

	private val realScratch = EdgeList()

	private fun isRealEdge(from: Long, to: Long): Boolean {
		realScratch.clear()
		momentum.successors(edgeCache, from, realScratch)
		return to in realScratch
	}

	fun routePlan(
		snapshotRevision: Long,
		cancelled: () -> Boolean = { false },
	): CoarseRoutePlan? {
		val candidate = route(cancelled) ?: return null

		val nodes = candidate.nodes
		val edges = nodes.zipWithNext { from, to -> liveEdge(from, to) ?: return null }
		return CoarseRoutePlan(
			snapshotRevision = snapshotRevision,
			nodes = nodes,
			edges = edges,
			lowerBoundTicks = candidate.ticks,
			dependencies = edges.flatMapTo(HashSet()) { it.readSet },
		)
	}

	private fun liveEdge(from: Stance, to: Stance): CoarseEdge? =
		edgeCache.edgesFrom(from)
			.asSequence()
			.filter { it.to == to }
			.minWithOrNull(compareBy({ it.lowerBoundTicks }, { it.template.value }))

	fun routeFailureReport(): String = buildString {
		append("start=").append(PackedStance.stance(search.start))
		append(" g=%.1f rhs=%.1f".format(search.g(search.start), search.rhs(search.start)))
		append(" nodes=").append(graphSize)
		append(" queue=").append(search.queue.size())
		append(" descent=").append(search.routeDescentReport())
	}

	fun resynchronizedRoutePlan(
		snapshotRevision: Long,
		cancelled: () -> Boolean = { false },
		maxExpansions: Int = Int.MAX_VALUE,
	): CoarseRoutePlan? {
		repeat(ROUTE_RESYNCHRONIZATION_ROUNDS) {
			if (cancelled()) return null
			val stale = route()?.nodes ?: return null
			if (!synchronizeStances(stale)) return routePlan(snapshotRevision)
			repair(maxExpansions = maxExpansions, cancelled = cancelled)
			routePlan(snapshotRevision)?.let { return it }
		}
		return null
	}

	private fun synchronizeStances(affected: Iterable<Stance>): Boolean {
		edgeCache.invalidateStances(affected)
		val lifted = LongArrayList()
		for (stance in affected) {
			lifted.add(PackedStance.pack(stance, SpeedClass.MOVING))
			lifted.add(PackedStance.pack(stance, SpeedClass.STOPPED))
		}
		return search.synchronizeAffected(lifted)
	}

	private companion object {

		const val ROUTE_RESYNCHRONIZATION_ROUNDS = 64

		const val ROUTE_DESCENT_EXPANSIONS = 200_000

		const val SEARCH_FIELD_TICKS = 36.0

		const val SEARCH_FIELD_EXPANSIONS = 20_000

		const val REPAIR_SLICE = 512

		const val FORWARD_SHARE = 2
	}

	fun valueField(): CoarseValueField = CoarseValueField(
		view = view,
		moves = moves,
		goal = goalStance,
		labelAt = ::nodeLabel,
		edgeProvider = edgeCache::edgesFrom,
		labelRevision = search::revision,
	)

	fun labels(around: Stance, radius: Int, height: Int): FieldLabels = labels(listOf(around), emptyList(), radius, 0, height)

	fun labels(around: List<Stance>, route: List<Stance>, radius: Int, band: Int, height: Int): FieldLabels {
		val cells = LongOpenHashSet()
		var minY = Int.MAX_VALUE
		var maxY = Int.MIN_VALUE
		for (node in route) {
			for (cellX in ((node.x - band) shr 4)..((node.x + band) shr 4)) {
				for (cellZ in ((node.z - band) shr 4)..((node.z + band) shr 4)) cells.add(PathingChunk.key(cellX, cellZ))
			}
			minY = minOf(minY, node.y - height)
			maxY = maxOf(maxY, node.y + height)
		}
		return FieldLabels(
			search.labels { node ->
				val x = PackedStance.unpackX(node)
				val y = PackedStance.unpackY(node)
				val z = PackedStance.unpackZ(node)
				around.any { abs(x - it.x) <= radius && abs(z - it.z) <= radius && abs(y - it.y) <= height } ||
						y in minY..maxY && cells.contains(PathingChunk.key(x shr 4, z shr 4))
			},
		)
	}

	private fun nodeLabel(stance: Stance, speed: SpeedClass): Double {
		val node = PackedStance.pack(stance, speed)
		return minOf(search.g(node), search.rhs(node))
	}

	fun chunksChanged(chunks: Iterable<PathingChunk>) = terrainChanged(chunks.map(CellBox::column))

	fun terrainChanged(
		changed: List<CellBox>,
		maxStances: Int = Int.MAX_VALUE,
		arrivalsOnly: Boolean = false,
	) {
		updateGoalKnowledge()
		anchors.refresh(changed)
		val origins = changed.map(moves::originsReading)
		if (changed.isNotEmpty()) {
			val bounds = CellBox.enclosing(origins + edgeCache.fallReaders(changed))
			search.forEachKnownNode { node ->
				if (!bounds.contains(PackedStance.unpackX(node), PackedStance.unpackY(node), PackedStance.unpackZ(node))) return@forEachKnownNode
				val key = PackedStance.stanceKey(node)
				if (CoarseEdgeCache.covers(origins, key) && !(arrivalsOnly && edgeCache.isComplete(key)) || edgeCache.fallsRead(changed, key)) pendingSync.add(key)
			}
		}
		edgeCache.invalidate(changed, arrivalsOnly)
		if (grounded == view.mayBounce) {
			grounded = !view.mayBounce
			search.rekey()
		}
		continueSync(maxStances)
	}

	fun continueSync(maxStances: Int = Int.MAX_VALUE): Boolean {
		if (pendingSync.isEmpty()) return false
		if (maxStances <= 0) return true
		val ordered = pendingSync.toLongArray()
		ordered.sort()
		val count = minOf(maxStances, ordered.size)
		syncedStances += count
		val lifted = LongArrayList(count * 2)
		for (i in 0 until count) {
			val key = ordered[i]
			pendingSync.remove(key)
			lifted.add(PackedStance.withSpeed(key, SpeedClass.MOVING))
			lifted.add(key)
		}
		search.synchronizeAffected(lifted)
		return pendingSync.isNotEmpty()
	}

	private val pendingSync = LongOpenHashSet()
}
