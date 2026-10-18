package com.lambda.pathing.debug

import com.lambda.Lambda.LOG
import com.lambda.pathing.actions.CoarseEdge
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.core.CellBox
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.PathingSection
import com.lambda.pathing.core.Stance
import com.lambda.pathing.search.CandidatePath
import com.lambda.pathing.search.SearchProbe
import com.lambda.pathing.search.SearchStats
import com.lambda.pathing.search.SearchTreeView
import com.lambda.pathing.rollout.TrajectoryDiagnostic
import com.lambda.pathing.rollout.TrajectoryRollout
import com.lambda.pathing.world.CoarseVoxelView
import net.minecraft.util.math.Vec3d
import kotlin.math.ceil
import kotlin.math.floor

object PlanningDebugChannel : SearchProbe {
	private const val MAX_IMPROVEMENTS = 500

	class Attempt(private val rollout: TrajectoryRollout, val certified: Boolean) {
		val points: List<Vec3d> by lazy(LazyThreadSafetyMode.PUBLICATION) {
			val frames = rollout.frames
			val points = ArrayList<Vec3d>(frames.size / DECIMATION + 2)
			points += rollout.initialState.position
			for (index in frames.indices step DECIMATION) points += frames[index].state.position
			if (frames.isNotEmpty() && (frames.size - 1) % DECIMATION != 0) points += frames.last().state.position
			points
		}
	}

	@Volatile
	var isActive = false
		private set

	@Volatile
	var coarseRoute: CoarseRoutePlan? = null
		private set

	@Volatile
	private var attemptView: List<Attempt>? = null

	val attempts: List<Attempt>
		get() = attemptView ?: synchronized(ring) { ArrayList(ring).also { attemptView = it } }

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

	@Volatile
	var candidateLines: List<CandidatePath> = emptyList()
		private set

	@Volatile
	var improvements: List<ImprovementDiff> = emptyList()
		private set

	fun publishImprovement(diff: ImprovementDiff) {
		improvements = (improvements + diff).takeLast(MAX_IMPROVEMENTS)
	}

	override val candidatesEnabled: Boolean get() = isActive

	override val treeEnabled: Boolean get() = isActive && treeWanted

	override fun candidates(lines: List<CandidatePath>) {
		if (isActive) candidateLines = lines
	}

	@Volatile
	var tree: SearchTreeView? = null
		private set

	@Volatile
	var stats: SearchStats? = null
		private set

	@Volatile
	var treeWanted: Boolean = false

	@Volatile
	var graphWanted: Boolean = false

	@Volatile
	private var lastGraphMillis = 0L

	fun refreshGraph(planner: CoarsePlanner, around: Vec3d) {
		if (!isActive || !graphWanted) return
		if (System.currentTimeMillis() - lastGraphMillis < GRAPH_REFRESH_MILLIS) return
		publishGraph(planner, around)
	}

	@Volatile
	var hudWanted: Boolean = false

	class CoarseSample(val frames: Long, val graphNodes: Int, val anchors: Int, val settleMillis: Long)

	@Volatile
	var coarse: CoarseSample? = null
		private set

	fun publishCoarse(sample: CoarseSample) {
		if (isActive) coarse = sample
	}

	@Volatile
	var lastExhaustion: SearchStats? = null
		private set

	@Volatile
	var lastExhaustionLedger: String = ""
		private set

	fun publishExhaustion(report: SearchStats, ledger: String) {
		lastExhaustion = report
		lastExhaustionLedger = ledger
	}

	override fun tree(view: SearchTreeView) {
		if (isActive) tree = view
	}

	override fun stats(view: SearchStats) {
		if (isActive) stats = view
	}

	override fun blocked(frame: Int, section: PathingSection, capturable: Boolean, stance: Stance, movement: MovementId) {
		if (verbose) LOG.info(
			"[blocked] section ({}, {}, {}) capturable={} frame={} anchor={} action={}",
			section.x, section.y, section.z, capturable, frame, stance, movement,
		)
	}

	@Volatile
	var graph: GraphSample? = null
		private set

	@Volatile
	private var graphLimits: GraphViewLimits = GraphViewLimits()

	fun publishGraph(planner: CoarsePlanner, around: Vec3d) {
		if (!isActive) return
		lastGraphMillis = System.currentTimeMillis()
		val goal = planner.goalStance
		val view = planner.view
		val limits = graphLimits
		val radiusSquared = limits.radius * limits.radius

		val reach = ceil(limits.radius).toInt() + 1
		val bodyX = floor(around.x).toInt()
		val bodyY = floor(around.y).toInt()
		val bodyZ = floor(around.z).toInt()
		val box = CellBox((bodyX - reach)..(bodyX + reach), (bodyY - reach)..(bodyY + reach), (bodyZ - reach)..(bodyZ + reach))
		val within = ArrayList<SampledStance>()
		planner.forEachGraphStanceWithin(box) { x, y, z ->
			val stance = Stance(x, y, z)
			val distance = distanceSquared(stance, around)
			if (distance <= radiusSquared) within += SampledStance(stance, distance, planner.stanceCost(stance))
		}
		within.sortWith(compareBy({ !it.cost.isFinite() }, { it.distance }))
		val picked = within.subList(0, minOf(within.size, limits.cells))
		val stances = picked.map { it.stance }

		val drawn = stances.toHashSet()
		val sampled = picked.map {
			GraphNode(
				pos = center(view, it.stance),
				cost = it.cost,
				frontier = planner.inFrontier(it.stance),
				anchor = planner.isAnchor(it.stance),
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

			val real = successors.filterKeys { to -> to in drawn && !(to == goal && planner.isAnchor(from)) }
			if (real.isEmpty()) return@forEach

			val best = real.entries.minByOrNull { (to, cost) -> cost + planner.stanceCost(to) }
				?.takeIf { (to, cost) -> (cost + planner.stanceCost(to)).isFinite() }
				?.key

			knownEdges += real.size
			if (edges.size >= limits.edges) return@forEach
			real.forEach { (to, cost) ->
				if (edges.size < limits.edges) {
					edges += GraphEdge(
						from = center(view, from),
						to = center(view, to),
						cost = cost,
						policy = to == best,
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
			total = planner.graphSize,
			totalEdges = knownEdges,
		)
	}

	private class SampledStance(val stance: Stance, val distance: Double, val cost: Double)

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
		clearSearchViews()
		isActive = enabled
	}

	fun publishRoute(route: CoarseRoutePlan) {
		if (isActive) coarseRoute = route
	}

	override fun attempt(rollout: TrajectoryRollout, certified: Boolean, diagnostic: TrajectoryDiagnostic?) {
		if (!isActive) return
		synchronized(ring) {
			ring += Attempt(rollout, certified)
			while (ring.size > MAX_ATTEMPTS) ring.removeFirst()
			attemptView = null
		}
	}

	fun reset() {
		isActive = false
		coarseRoute = null
		lastExhaustion = null
		lastExhaustionLedger = ""
		graph = null
		coarse = null
		improvements = emptyList()
		clearSearchViews()
	}

	private fun clearSearchViews() {
		synchronized(ring) {
			ring.clear()
			attemptView = null
		}
		candidateLines = emptyList()
		tree = null
		stats = null
	}

	private val verbose = java.lang.Boolean.getBoolean("lambda.pathing.dumpFailures")

	private const val MAX_ATTEMPTS = 32

	private const val GRAPH_REFRESH_MILLIS = 1500L

	private const val DECIMATION = 2
}
