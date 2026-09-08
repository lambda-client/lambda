package com.lambda.pathing.coarse

import com.lambda.pathing.actions.CoarseEdge
import com.lambda.pathing.core.PathingChunk
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.VoxelPos
import com.lambda.pathing.graph.CoarseRouteCandidate
import com.lambda.pathing.graph.LongDStarLite
import com.lambda.pathing.graph.LongLazyGraph
import com.lambda.pathing.graph.TailCost
import com.lambda.pathing.world.CoarseVoxelView
import it.unimi.dsi.fastutil.longs.LongArrayList
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * The coarse layer's D* runs over [PackedStance] nodes -- stance times [SpeedClass] packed
 * into one long -- so the field can price momentum honestly; see [MomentumRules].
 * Everything public stays stance-shaped: callers never meet the class dimension, they
 * meet stance values that are the best over both classes, and routes whose brake
 * self-edges have been folded away.
 */
class CoarsePlanner(

	val view: CoarseVoxelView,
	val moves: SimpleMoveLibrary,
	start: Stance,
	val goal: GoalShape,

	private val sweepBudget: Int = 40_000,

	/** See [FrontierAnchors.NOTHING_CAPTURABLE]: capturable unknowns never anchor. */
	private val capturable: (Int, Int) -> Boolean = FrontierAnchors.NOTHING_CAPTURABLE,

	/** Reported for every capturable unknown that suppressed an anchor; see [FrontierAnchors.sweep]. */
	private val onCaptureLag: (Int, Int, Int) -> Unit = { _, _, _ -> },
) {

	/** The one-stance goal every caller has today: a [GoalShape.Point]. */
	constructor(
		view: CoarseVoxelView,
		moves: SimpleMoveLibrary,
		start: Stance,
		goal: Stance,
		sweepBudget: Int = 40_000,
		capturable: (Int, Int) -> Boolean = FrontierAnchors.NOTHING_CAPTURABLE,
		onCaptureLag: (Int, Int, Int) -> Unit = { _, _, _ -> },
	) : this(view, moves, start, GoalShape.Point(goal, moves), sweepBudget, capturable, onCaptureLag)

	private val anchors = HashMap<Stance, Double>()

	val goalStance: Stance = goal.anchorStance

	private val goalNode: Long = PackedStance.pack(goalStance, SpeedClass.STOPPED)

	private val edgeCache = CoarseEdgeCache(view, moves)

	private val graph = LongLazyGraph(
		successorProvider = { node, sink ->
			MomentumRules.successors(edgeCache, node, sink)
			// Put semantics on purpose: an anchor's optimistic edge to the goal replaces
			// any real edge to the goal node, as the old `base + optimistic` did.
			if (anchors.isNotEmpty()) {
				val cost = anchors[PackedStance.stance(node)]
				if (cost != null) sink.add(goalNode, cost)
			}
		},
		predecessorProvider = { node, sink ->
			MomentumRules.predecessors(edgeCache, node, sink)
			if (node == goalNode && anchors.isNotEmpty()) {
				for ((stance, cost) in anchors) {
					sink.add(PackedStance.pack(stance, SpeedClass.MOVING), cost)
					sink.add(PackedStance.pack(stance, SpeedClass.STOPPED), cost)
				}
			}
		},
	)

	val search = LongDStarLite(
		graph = graph,
		start = PackedStance.pack(start, SpeedClass.STOPPED),
		goal = goalNode,
		heuristic = { a, b ->
			moves.heuristic(
				PackedStance.unpackX(a), PackedStance.unpackY(a), PackedStance.unpackZ(a),
				PackedStance.unpackX(b), PackedStance.unpackY(b), PackedStance.unpackZ(b),
			)
		},
		describe = PackedStance::describe,
	)

	val graphSize: Int get() = graph.size

	/**
	 * Visits every distinct stance the graph has adopted, once, without materialising a
	 * set: a stance whose STOPPED node is known is visited there; a MOVING-only stance is
	 * visited at its MOVING node.
	 */
	fun forEachGraphStance(action: (x: Int, y: Int, z: Int) -> Unit) {
		val iterator = graph.nodes.iterator()
		while (iterator.hasNext()) {
			val node = iterator.nextLong()
			if (PackedStance.speedBit(node) != 0 && graph.contains(PackedStance.withSpeed(node, SpeedClass.STOPPED))) continue
			action(PackedStance.unpackX(node), PackedStance.unpackY(node), PackedStance.unpackZ(node))
		}
	}

	/** Best-over-classes cost to goal, for observers that think in stances. */
	fun stanceCost(stance: Stance): Double = minOf(
		search.g(PackedStance.pack(stance, SpeedClass.MOVING)),
		search.g(PackedStance.pack(stance, SpeedClass.STOPPED)),
	)

	fun inFrontier(stance: Stance): Boolean =
		PackedStance.pack(stance, SpeedClass.MOVING) in search.queue ||
				PackedStance.pack(stance, SpeedClass.STOPPED) in search.queue

	/** The transitions the lazy graph has materialised out of [node]; empty when the cell was never expanded. */
	fun knownEdgesOf(node: Stance): List<CoarseEdge> = edgeCache.cachedEdgesFrom(node) ?: emptyList()

	fun knownSuccessorsOf(node: Stance): Map<Stance, Double> {
		val merged = HashMap<Stance, Double>()
		for (speed in SpeedClass.entries) {
			val edges = graph.knownSuccessors(PackedStance.pack(node, speed))
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
		timeBudget: Duration = 5.milliseconds,
		maxExpansions: Int = Int.MAX_VALUE,
		cancelled: () -> Boolean = { false },
	): LongDStarLite.ComputeResult = search.computeShortestPath(timeBudget, maxExpansions, cancelled)

	fun updateStart(start: Stance) = search.updateStart(PackedStance.pack(start, SpeedClass.STOPPED))

	val optimisticAnchors: Map<Stance, Double> get() = HashMap(anchors)

	fun optimisticEdgeCost(from: Stance): Double = SpeedClass.entries.minOf {
		graph.knownSuccessors(PackedStance.pack(from, it)).costOf(goalNode)
	}

	private fun updateAnchorEdges(anchor: Stance, cost: Double) {
		search.updateEdge(PackedStance.pack(anchor, SpeedClass.MOVING), goalNode, cost)
		search.updateEdge(PackedStance.pack(anchor, SpeedClass.STOPPED), goalNode, cost)
	}

	fun advanceFrontier(probed: Map<Stance, Double>): Boolean {
		var changed = false

		// Retirement waits for cells to be KNOWN; creation refuses merely capturable
		// unknowns. The asymmetry is deliberate: docs/decisions/anchor-lifecycle.md.
		val refused = FrontierAnchors.refusesOptimism(view, moves, goalStance)
		val retired = anchors.keys.filterTo(ArrayList()) {
			refused || !FrontierAnchors.bordersUnknown(view, it)
		}
		retired.forEach {
			anchors.remove(it)
			updateAnchorEdges(it, Double.POSITIVE_INFINITY)
			changed = true
		}

		probed.forEach { (anchor, cost) ->
			if (anchor != goalStance && anchors[anchor] != cost) {
				anchors[anchor] = cost
				updateAnchorEdges(anchor, cost)
				changed = true
			}
		}
		return changed
	}

	fun retireAllAnchors(): Boolean {
		if (anchors.isEmpty()) return false
		anchors.keys.toList().forEach { anchor ->
			anchors.remove(anchor)
			updateAnchorEdges(anchor, Double.POSITIVE_INFINITY)
		}
		return true
	}

	/**
	 * Mints anchors by flooding from [from] through real edges, so every anchor is
	 * reachable by construction. Optimism is a route-terminal device, never a movement;
	 * see docs/decisions/anchor-lifecycle.md.
	 */
	fun advanceReachableFrontier(
		from: Stance = PackedStance.stance(search.start),
		cancelled: () -> Boolean = { false },
	): Boolean {
		if (from == goalStance || FrontierAnchors.refusesOptimism(view, moves, goalStance)) {
			return advanceFrontier(emptyMap())
		}
		// Resume the flood where knowledge grew. Only origins that read an unknown cell can
		// gain edges through capture, so re-expanding those (with the interior already
		// visited) reaches everything a fresh flood would. Mutations and a moved start
		// invalidate that argument and force a full sweep.
		val incremental = sweepFrom == from && !sweepDirty && sweptVisited.isNotEmpty()
		val seeds: Collection<Stance> = if (incremental) ArrayList(sweptIncomplete) else {
			if (!moves.isStance(view, from)) return advanceFrontier(emptyMap())
			sweptVisited.clear()
			sweptIncomplete.clear()
			sweepFrom = from
			sweepDirty = false
			listOf(from)
		}
		val probed = FrontierAnchors.sweep(
			view, moves, seeds, goalStance, sweptVisited, sweptIncomplete,
			maxNodes = sweepBudget, cancelled = cancelled,
			capturable = capturable, onCaptureLag = onCaptureLag,
			edges = edgeCache::edgesFrom,
		)
		return advanceFrontier(probed)
	}

	private val sweptVisited = HashSet<Stance>()
	private val sweptIncomplete = HashSet<Stance>()
	private var sweepFrom: Stance? = null
	private var sweepDirty = false

	fun discoverReachableFrontier(
		cancelled: () -> Boolean = { false },
		maxExpansions: Int = Int.MAX_VALUE,
	): Boolean {
		val changed = advanceReachableFrontier(cancelled = cancelled)
		if (changed) repair(timeBudget = Duration.INFINITE, maxExpansions = maxExpansions, cancelled = cancelled)
		return changed
	}

	fun expandField(
		extraTicks: Double,
		timeBudget: Duration = 50.milliseconds,
		maxExpansions: Int = Int.MAX_VALUE,
		cancelled: () -> Boolean = { false },
	): LongDStarLite.ComputeResult = search.expandField(extraTicks, timeBudget, maxExpansions, cancelled)

	fun route(
		maxLength: Int = 10_000,
		cancelled: () -> Boolean = { false },
	): CoarseRouteCandidate<Stance>? {
		val candidate = search.computeRouteCandidate(
			maxLength = maxLength,
			maxExpansions = ROUTE_DESCENT_EXPANSIONS,
			cancelled = cancelled,
		) ?: return null
		// Brake self-edges fold away: consecutive nodes sharing a stance are one visit.
		val nodes = candidate.nodes
		val stances = ArrayList<Stance>(nodes.size)
		var previous = 0L
		for (i in 0 until nodes.size) {
			val node = nodes.getLong(i)
			if (i == 0 || !PackedStance.sameStance(previous, node)) stances += PackedStance.stance(node)
			previous = node
		}
		return CoarseRouteCandidate(
			nodes = stances,
			ticks = candidate.ticks,
			exactFromStart = candidate.exactFromStart,
			routeVersion = candidate.routeVersion,
		)
	}

	fun routePlan(
		snapshotRevision: Long,
		maxLength: Int = 10_000,
		cancelled: () -> Boolean = { false },
	): CoarseRoutePlan? {
		val candidate = route(maxLength, cancelled) ?: return null

		val nodes =
			if (candidate.nodes.size >= 2 && goal.satisfied(candidate.nodes.last()) &&
				candidate.nodes[candidate.nodes.size - 2] in anchors
			) candidate.nodes.dropLast(1) else candidate.nodes
		val edges = nodes.zipWithNext { from, to -> liveEdge(from, to) ?: return null }
		return plan(snapshotRevision, nodes, edges, candidate.ticks, candidate.exactFromStart)
	}

	private fun liveEdge(from: Stance, to: Stance): CoarseEdge? =
		edgeCache.edgesFrom(from)
			.asSequence()
			.filter { it.to == to }
			.minWithOrNull(compareBy({ it.lowerBoundTicks }, { it.id.template.value }))

	fun routeFailureReport(): String = buildString {
		append("start=").append(PackedStance.stance(search.start))
		append(" g=%.1f rhs=%.1f".format(search.g(search.start), search.rhs(search.start)))
		append(" nodes=").append(graphSize)
		append(" queue=").append(search.queue.size())
		append(" descent=").append(search.routeDescentReport())
	}

	private fun plan(
		snapshotRevision: Long,
		nodes: List<Stance>,
		edges: List<CoarseEdge>,
		ticks: Double,
		exactFromStart: Boolean,
	) = CoarseRoutePlan(
		snapshotRevision = snapshotRevision,
		routeVersion = search.routeVersion,
		nodes = nodes.toList(),
		edges = edges.toList(),
		lowerBoundTicks = ticks,
		exactFromStart = exactFromStart,
		dependencies = edges.flatMapTo(HashSet()) { it.readSet },
		tailCosts = nodes.map { stanceTailCost(it) },
		heuristicCaps = moves.heuristicCaps,
	)

	fun resynchronizedRoutePlan(
		snapshotRevision: Long,
		cancelled: () -> Boolean = { false },
		maxLength: Int = 10_000,
		maxRounds: Int = ROUTE_RESYNCHRONIZATION_ROUNDS,
		maxExpansions: Int = Int.MAX_VALUE,
	): CoarseRoutePlan? {
		repeat(maxRounds) {
			if (cancelled()) return null
			val stale = route(maxLength)?.nodes ?: return null
			val synchronized = synchronizeStances(stale)
			if (synchronized.edgesRemoved + synchronized.edgesChanged + synchronized.edgesAdded == 0) {
				return null
			}
			repair(timeBudget = Duration.INFINITE, maxExpansions = maxExpansions, cancelled = cancelled)
			routePlan(snapshotRevision, maxLength)?.let { return it }
		}
		return null
	}

	fun tailCost(node: Stance = PackedStance.stance(search.start)): TailCost = stanceTailCost(node)

	/**
	 * The better-informed of the two class nodes: exactness first, then cost. A raw min
	 * over classes would prefer an unexplored MOVING node's bound over an exact STOPPED answer.
	 */
	private fun stanceTailCost(node: Stance): TailCost {
		val moving = search.tailCost(PackedStance.pack(node, SpeedClass.MOVING))
		val stopped = search.tailCost(PackedStance.pack(node, SpeedClass.STOPPED))
		return when {
			moving is TailCost.Exact && stopped is TailCost.Exact ->
				if (moving.ticks <= stopped.ticks) moving else stopped
			moving is TailCost.Exact -> moving
			stopped is TailCost.Exact -> stopped
			moving is TailCost.Unreachable -> stopped
			stopped is TailCost.Unreachable -> moving
			else -> if (moving.lowerBound <= stopped.lowerBound) moving else stopped
		}
	}

	private fun synchronizeStances(affected: Iterable<Stance>): LongDStarLite.SynchronizationResult {
		// Synchronization regenerates from the live view: memoized edges go first, even
		// for callers that arrive without a world-change notification.
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
	}

	fun valueField(): CoarseValueField = CoarseValueField(
		view = view,
		moves = moves,
		label = { stance ->
			minOf(nodeLabel(stance, SpeedClass.MOVING), nodeLabel(stance, SpeedClass.STOPPED))
		},
		goal = goalStance,
		labelAt = ::nodeLabel,
		edgeProvider = edgeCache::edgesFrom,
	)

	private fun nodeLabel(stance: Stance, speed: SpeedClass): Double {
		val node = PackedStance.pack(stance, speed)
		return minOf(search.g(node), search.rhs(node))
	}

	fun worldChanged(changed: Iterable<VoxelPos>): LongDStarLite.SynchronizationResult {
		sweepDirty = true
		edgeCache.invalidateVoxels(changed)
		val affected = HashSet<Stance>()
		changed.forEach { affected += moves.affectedOrigins(it) }
		return synchronizeStances(affected)
	}

	/**
	 * Chunk arrivals: evict the affected cache columns at once, then regenerate the
	 * affected graph nodes' edges -- all of them, or at most [maxStances] now with the
	 * rest left in [pendingSyncSize] for [continueSync]. Slicing keeps a streaming world
	 * from blocking the search thread; a node not yet resynchronised simply keeps its
	 * edges from before the arrival, which is knowledge lag, not inconsistency.
	 */
	fun chunksChanged(
		chunks: Iterable<PathingChunk>,
		maxStances: Int = Int.MAX_VALUE,
		arrivalsOnly: Boolean = false,
	): LongDStarLite.SynchronizationResult {
		// Range-based cache eviction, not graph-node-filtered: the cache can hold
		// stances the graph never adopted (steering reads, rim origins). For pure
		// arrivals only origins that read an unknown cell can change; the rest keep
		// both their cache entry and their graph edges.
		if (!arrivalsOnly) sweepDirty = true
		val ranges = chunks.map(moves::originColumnRanges)
		forEachGraphStance { x, y, z ->
			if (ranges.any { (xs, zs) -> x in xs && z in zs }) {
				if (arrivalsOnly && edgeCache.isComplete(Stance(x, y, z))) return@forEachGraphStance
				pendingSync.add(PackedStance.pack(x, y, z, SpeedClass.STOPPED))
			}
		}
		edgeCache.invalidateChunks(chunks, arrivalsOnly)
		return continueSync(maxStances)
	}

	/** Stances whose edges still await regeneration after a sliced [chunksChanged]. */
	val pendingSyncSize: Int get() = pendingSync.size

	/** Regenerates up to [maxStances] pending stances, lowest packed key first. */
	fun continueSync(maxStances: Int = Int.MAX_VALUE): LongDStarLite.SynchronizationResult {
		if (pendingSync.isEmpty()) return LongDStarLite.SynchronizationResult(0, 0, 0, 0)
		val ordered = pendingSync.toLongArray()
		ordered.sort()
		val count = minOf(maxStances, ordered.size)
		val lifted = LongArrayList(count * 2)
		for (i in 0 until count) {
			val key = ordered[i]
			pendingSync.remove(key)
			lifted.add(PackedStance.withSpeed(key, SpeedClass.MOVING))
			lifted.add(key)
		}
		return search.synchronizeAffected(lifted)
	}

	private val pendingSync = LongOpenHashSet()
}
