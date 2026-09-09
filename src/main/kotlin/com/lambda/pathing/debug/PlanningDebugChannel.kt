package com.lambda.pathing.debug

import com.lambda.pathing.actions.CoarseEdge
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.Stance
import com.lambda.pathing.search.SearchStatsView
import com.lambda.pathing.search.SearchTreeView
import com.lambda.pathing.rollout.TrajectoryDiagnostic
import com.lambda.pathing.rollout.TrajectoryRollout
import com.lambda.pathing.world.CoarseVoxelView
import net.minecraft.util.math.Vec3d

object PlanningDebugChannel {
	class Attempt(
		val points: List<Vec3d>,
		val certified: Boolean,
		val diagnostic: String?,
	)

	@Volatile
	private var active = false

	val isActive: Boolean get() = active

	@Volatile
	var coarseRoute: CoarseRoutePlan? = null
		private set

	@Volatile
	var attempts: List<Attempt> = emptyList()
		private set

	class GraphNode(
		val pos: Vec3d,
		val cost: Double,
		val frontier: Boolean,

		val anchor: Boolean,
	)

	class GraphEdge(
		val from: Vec3d,
		val to: Vec3d,
		val cost: Double,
		val policy: Boolean,

		@Suppress("unused")
	val fromCost: Double,
		val toCost: Double,
		val movement: MovementId?,
	)

	class GraphSample(
		val nodes: List<GraphNode>,
		val edges: List<GraphEdge>,
		val cheapest: Double,
		val dearest: Double,
		val total: Int,

		val totalEdges: Int,
	)

	data class GraphViewLimits(
		val radius: Double = 48.0,
		val cells: Int = 3072,
		val edges: Int = 12_288,
	)

	class CandidateLine(val points: List<Vec3d>, val best: Boolean)

	@Volatile
	var candidateLines: List<CandidateLine> = emptyList()
		private set

	fun publishCandidates(lines: List<CandidateLine>) {
		if (active) candidateLines = lines
	}

	@Volatile
	var tree: SearchTreeView? = null
		private set

	@Volatile
	var stats: SearchStatsView? = null
		private set

	@Volatile
	var treeWanted: Boolean = false

	@Volatile
	var graphWanted: Boolean = false

	@Volatile
	private var lastGraphMillis = 0L

	fun refreshGraph(planner: CoarsePlanner, around: Vec3d) {
		if (!active || !graphWanted) return
		val now = System.currentTimeMillis()
		if (now - lastGraphMillis < GRAPH_REFRESH_MILLIS) return
		lastGraphMillis = now
		publishGraph(planner, around)
	}

	@Volatile
	var hudWanted: Boolean = false

	@Volatile
	var lastExhaustion: com.lambda.pathing.search.SearchExhaustion? = null
		private set

	@Volatile
	var lastExhaustionLedger: String = ""
		private set

	@Volatile
	var lastExhaustionMillis: Long = 0L
		private set

	fun publishExhaustion(report: com.lambda.pathing.search.SearchExhaustion, ledger: String) {
		lastExhaustion = report
		lastExhaustionLedger = ledger
		lastExhaustionMillis = System.currentTimeMillis()
	}

	fun publishTree(view: SearchTreeView) {
		if (active) tree = view
	}

	fun publishStats(view: SearchStatsView) {
		if (active) stats = view
	}

	@Volatile
	var graph: GraphSample? = null
		private set

	@Volatile
	var graphLimits: GraphViewLimits = GraphViewLimits()
		private set

	fun publishGraph(planner: CoarsePlanner, around: Vec3d) {
		if (!active) return
		lastGraphMillis = System.currentTimeMillis()
		val anchors = planner.optimisticAnchors
		val goal = planner.goalStance
		val view = planner.view
		val limits = graphLimits
		val radiusSquared = limits.radius * limits.radius

		var total = 0
		val within = ArrayList<Pair<Stance, Double>>()
		planner.forEachGraphStance { x, y, z ->
			total++
			val stance = Stance(x, y, z)
			val distance = distanceSquared(stance, around)
			if (distance <= radiusSquared) within += stance to distance
		}
		val stances = within.asSequence()
			.sortedWith(compareBy({ !planner.stanceCost(it.first).isFinite() }, { it.second }))
			.take(limits.cells)
			.map { (stance, _) -> stance }
			.toList()

		val drawn = stances.toHashSet()
		val sampled = stances.map { stance ->
			GraphNode(
				pos = center(view, stance),
				cost = planner.stanceCost(stance),
				frontier = planner.inFrontier(stance),
				anchor = stance in anchors,
			)
		}

		var knownEdges = 0
		val edges = ArrayList<GraphEdge>()
		stances.forEach { from ->
			val successors = planner.knownSuccessorsOf(from)
			val cheapest = HashMap<Stance, CoarseEdge>()
			planner.knownEdgesOf(from).forEach { edge ->
				val existing = cheapest[edge.to]
				if (existing == null || edge.lowerBoundTicks < existing.lowerBoundTicks) cheapest[edge.to] = edge
			}

			val real = successors.filterKeys { to -> to in drawn && !(to == goal && from in anchors) }
			if (real.isEmpty()) return@forEach

			val best = real.entries.minByOrNull { (to, cost) -> cost + planner.stanceCost(to) }
				?.takeIf { (to, cost) -> (cost + planner.stanceCost(to)).isFinite() }
				?.key

			knownEdges += real.size
			if (edges.size >= limits.edges) return@forEach
			val fromCost = planner.stanceCost(from)
			real.forEach { (to, cost) ->
				if (edges.size < limits.edges) {
					edges += GraphEdge(
						from = center(view, from),
						to = center(view, to),
						cost = cost,
						policy = to == best,
						fromCost = fromCost,
						toCost = planner.stanceCost(to),
						movement = cheapest[to]?.movement,
					)
				}
			}
		}

		val finite = sampled.map { it.cost }.filter { it.isFinite() }
		graph = GraphSample(
			nodes = sampled,
			edges = edges,
			cheapest = finite.minOrNull() ?: 0.0,
			dearest = finite.maxOrNull() ?: 0.0,
			total = total,
			totalEdges = knownEdges,
		)
	}

	private fun center(view: CoarseVoxelView, stance: Stance) = Vec3d(
		stance.x + 0.5,
		stance.y + view.surfaceOffset(stance.x, stance.y - 1, stance.z),
		stance.z + 0.5,
	)

	private fun distanceSquared(stance: Stance, around: Vec3d): Double {
		val dx = stance.x + 0.5 - around.x
		val dy = stance.y - around.y
		val dz = stance.z + 0.5 - around.z
		return dx * dx + dy * dy + dz * dz
	}

	private val ring = ArrayDeque<Attempt>()

	fun begin(enabled: Boolean, limits: GraphViewLimits = GraphViewLimits()) {
		graphLimits = limits
		synchronized(ring) { ring.clear() }
		attempts = emptyList()
		candidateLines = emptyList()
		tree = null
		stats = null

		active = enabled
	}

	fun publishRoute(route: CoarseRoutePlan) {
		if (active) coarseRoute = route
	}

	fun publishAttempt(rollout: TrajectoryRollout, certified: Boolean, diagnostic: TrajectoryDiagnostic?) {
		if (!active) return
		val points = ArrayList<Vec3d>(rollout.frames.size / DECIMATION + 2)
		points += rollout.initialState.position
		for (index in rollout.frames.indices step DECIMATION) {
			points += rollout.frames[index].state.position
		}
		rollout.frames.lastOrNull()?.let { last ->
			if ((rollout.frames.size - 1) % DECIMATION != 0) points += last.state.position
		}
		val attempt = Attempt(points, certified, diagnostic?.let { it::class.simpleName })
		synchronized(ring) {
			ring += attempt
			while (ring.size > MAX_ATTEMPTS) ring.removeFirst()
			attempts = ArrayList(ring)
		}
	}

	fun reset() {
		active = false
		coarseRoute = null
		lastExhaustion = null
		lastExhaustionLedger = ""
		attempts = emptyList()
		candidateLines = emptyList()
		tree = null
		stats = null
		graph = null
		synchronized(ring) { ring.clear() }
	}

	private const val MAX_ATTEMPTS = 32

	private const val GRAPH_REFRESH_MILLIS = 1500L

	private const val DECIMATION = 2
}
