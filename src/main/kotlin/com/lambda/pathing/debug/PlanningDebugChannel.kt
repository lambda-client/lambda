package com.lambda.pathing.debug

import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.coarse.Stance
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.trajectory.TrajectoryDiagnostic
import com.lambda.pathing.trajectory.TrajectoryRollout
import net.minecraft.util.math.Vec3d

object PlanningDebugChannel {
    class Attempt(
        val points: List<Vec3d>,
        val certified: Boolean,
        val diagnostic: String?,
    )

    @Volatile
    private var active = false

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

        val fromCost: Double,
        val toCost: Double,
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
    var graph: GraphSample? = null
        private set

    @Volatile
    var graphLimits: GraphViewLimits = GraphViewLimits()
        private set

    fun publishGraph(planner: CoarsePlanner, around: Vec3d) {
        if (!active) return
        val nodes = planner.graphNodes
        val total = nodes.size
        val anchors = planner.optimisticAnchors
        val goal = planner.search.goal
        val view = planner.view
        val limits = graphLimits
        val radiusSquared = limits.radius * limits.radius

        val stances = nodes.asSequence()
            .map { stance -> stance to distanceSquared(stance, around) }
            .filter { (_, distance) -> distance <= radiusSquared }
            .sortedWith(compareBy({ !planner.search.g(it.first).isFinite() }, { it.second }))
            .take(limits.cells)
            .map { (stance, _) -> stance }
            .toList()

        val drawn = stances.toHashSet()
        val sampled = stances.map { stance ->
            GraphNode(
                pos = center(view, stance),
                cost = planner.search.g(stance),
                frontier = stance in planner.search.queue,
                anchor = stance in anchors,
            )
        }

        var knownEdges = 0
        val edges = ArrayList<GraphEdge>()
        stances.forEach { from ->
            val successors = planner.knownSuccessorsOf(from)

            val real = successors.filterKeys { to -> to in drawn && !(to == goal && from in anchors) }
            if (real.isEmpty()) return@forEach

            val best = real.entries.minByOrNull { (to, cost) -> cost + planner.search.g(to) }
                ?.takeIf { (to, cost) -> (cost + planner.search.g(to)).isFinite() }
                ?.key

            knownEdges += real.size
            if (edges.size >= limits.edges) return@forEach
            val fromCost = planner.search.g(from)
            real.forEach { (to, cost) ->
                if (edges.size < limits.edges) {
                    edges += GraphEdge(
                        from = center(view, from),
                        to = center(view, to),
                        cost = cost,
                        policy = to == best,
                        fromCost = fromCost,
                        toCost = planner.search.g(to),
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
        attempts = emptyList()
        candidateLines = emptyList()
        graph = null
        synchronized(ring) { ring.clear() }
    }

    private const val MAX_ATTEMPTS = 32

    private const val DECIMATION = 2
}
