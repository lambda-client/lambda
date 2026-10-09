package com.lambda.pathing.coarse

import com.lambda.pathing.actions.CoarseEdge
import com.lambda.pathing.core.CellBox
import com.lambda.pathing.core.PathingChunk
import com.lambda.pathing.core.Stance
import com.lambda.pathing.graph.CoarseRouteCandidate
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
	sweepBudget: Int = FrontierAnchors.SWEEP_MAX_NODES,
	capturable: (Int, Int) -> Boolean = FrontierAnchors.NOTHING_CAPTURABLE,
	onCaptureLag: (Int, Int, Int) -> Unit = { _, _, _ -> },
) {
	private val anchors = HashMap<Stance, Double>()

	private val goalNode: Long = PackedStance.pack(goalStance, SpeedClass.STOPPED)

	private val edgeCache = CoarseEdgeCache(view.cursor(), moves)

	private val momentum = MomentumRules(moves.templates)

	private val frontier = FrontierSweep(view, moves, goalStance, edgeCache, sweepBudget, capturable, onCaptureLag)

	private val graph = LongGraph(
		successorProvider = { node, sink ->
			momentum.successors(edgeCache, node, sink)

			if (anchors.isNotEmpty()) {
				val cost = anchors[PackedStance.stance(node)]
				if (cost != null) sink.add(goalNode, cost)
			}
		},
		predecessorProvider = { node, sink ->
			momentum.predecessors(edgeCache, node, sink)
			if (node == goalNode && anchors.isNotEmpty()) {
				for ((stance, cost) in anchors) {
					sink.add(PackedStance.pack(stance, SpeedClass.MOVING), cost)
					sink.add(PackedStance.pack(stance, SpeedClass.STOPPED), cost)
				}
			}
		},
	)

	private var grounded = !view.mayBounce

	private val search = LongDStarLite(
		graph = graph,
		start = PackedStance.pack(start, SpeedClass.STOPPED),
		goal = goalNode,
		heuristic = { a, b -> momentum.heuristic(a, b, grounded && b != goalNode) },
		describe = PackedStance::describe,
	)

	val graphSize: Int get() = search.knownNodeCount

	fun forEachGraphStance(action: (x: Int, y: Int, z: Int) -> Unit) {
		search.forEachKnownNode { node ->
			if (PackedStance.speed(node) != SpeedClass.MOVING || !search.isKnown(PackedStance.withSpeed(node, SpeedClass.STOPPED))) {
				action(PackedStance.unpackX(node), PackedStance.unpackY(node), PackedStance.unpackZ(node))
			}
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

	fun knownSuccessorsOf(node: Stance): Map<Stance, Double> {
		val merged = HashMap<Stance, Double>()
		for (speed in SpeedClass.entries) {
			val edges = search.knownSuccessors(PackedStance.pack(node, speed))
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
	): LongDStarLite.ComputeResult = search.computeShortestPath(Duration.INFINITE, maxExpansions, cancelled)

	fun updateStart(start: Stance, speed: SpeedClass = SpeedClass.STOPPED) = search.updateStart(PackedStance.pack(start, speed))

	val optimisticAnchors: Map<Stance, Double> get() = HashMap(anchors)

	private fun updateAnchorEdges(anchor: Stance, cost: Double) {
		search.updateEdge(PackedStance.pack(anchor, SpeedClass.MOVING), goalNode, cost)
		search.updateEdge(PackedStance.pack(anchor, SpeedClass.STOPPED), goalNode, cost)
	}

	fun advanceFrontier(probed: Map<Stance, Double>): Boolean {
		val refused = FrontierAnchors.refusesOptimism(view, moves, goalStance)
		val retired = anchors.keys.filterTo(ArrayList()) {
			refused || !FrontierAnchors.bordersUnknown(view, it)
		}
		retired.forEach(::retire)
		var changed = retired.isNotEmpty()

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
		anchors.keys.toList().forEach(::retire)
		return true
	}

	private fun retire(anchor: Stance) {
		anchors.remove(anchor)
		updateAnchorEdges(anchor, Double.POSITIVE_INFINITY)
	}

	fun advanceReachableFrontier(
		from: Stance = PackedStance.stance(search.start),
		cancelled: () -> Boolean = { false },
	): Boolean = advanceFrontier(frontier.anchorsFrom(from, cancelled))

	fun discoverReachableFrontier(
		cancelled: () -> Boolean = { false },
		maxExpansions: Int = Int.MAX_VALUE,
	): Boolean {
		val changed = advanceReachableFrontier(cancelled = cancelled)
		if (changed) repair(maxExpansions = maxExpansions, cancelled = cancelled)
		return changed
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
			if (i == 0 || !PackedStance.sameStance(previous, node)) stances += PackedStance.stance(node)
			previous = node
		}
		return CoarseRouteCandidate(
			nodes = stances,
			ticks = candidate.ticks,
			exactFromStart = candidate.exactFromStart,
		)
	}

	fun routePlan(
		snapshotRevision: Long,
		cancelled: () -> Boolean = { false },
	): CoarseRoutePlan? {
		val candidate = route(cancelled) ?: return null

		val nodes =
			if (candidate.nodes.size >= 2 && candidate.nodes.last() == goalStance &&
				candidate.nodes[candidate.nodes.size - 2] in anchors
			) candidate.nodes.dropLast(1) else candidate.nodes
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
			if (!synchronizeStances(stale)) return null
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
	}

	fun valueField(): CoarseValueField = CoarseValueField(
		view = view,
		moves = moves,
		goal = goalStance,
		labelAt = ::nodeLabel,
		edgeProvider = edgeCache::edgesFrom,
		labelRevision = search::revision,
	)

	fun labels(around: Stance, radius: Int, height: Int): FieldLabels = FieldLabels(
		search.labels { node ->
			abs(PackedStance.unpackX(node) - around.x) <= radius &&
					abs(PackedStance.unpackZ(node) - around.z) <= radius &&
					abs(PackedStance.unpackY(node) - around.y) <= height
		},
	)

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
		if (!arrivalsOnly) frontier.dirty = true
		val origins = changed.map(moves::originsReading)
		search.forEachKnownNode { node ->
			val key = PackedStance.stanceKey(node)
			if (CoarseEdgeCache.covers(origins, key) && !(arrivalsOnly && edgeCache.isComplete(key))) pendingSync.add(key)
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
		search.synchronizeAffected(lifted)
		return pendingSync.isNotEmpty()
	}

	private val pendingSync = LongOpenHashSet()
}
