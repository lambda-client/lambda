/*
 * Copyright 2026 Lambda
 */
package pathing

import com.lambda.pathing.movement.CoarseMoveCosts
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.SimpleMoveLibrary
import com.lambda.pathing.movement.SimpleMoveOptions
import com.lambda.pathing.core.Stance
import com.lambda.pathing.debug.PlanningDebugChannel
import com.lambda.pathing.prediction.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.prediction.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.prediction.SnapshotSimulationEnvironment
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration

/**
 * The debug view onto the search graph.
 *
 * Worth testing rather than eyeballing because the sampling has to hold two properties the
 * render depends on and neither is visible in a screenshot: it must stay bounded on a graph
 * of any size, and it must report honestly how much of the graph it left out.
 */
class PlanningGraphViewTest {
    @AfterTest
    fun tearDown() = PlanningDebugChannel.reset()

    @Test
    fun `the graph view samples around the body and reports what it left out`() {
        PlanningDebugChannel.begin(true)
        val planner = plannerOverFlatGround()
        planner.repair(Duration.INFINITE)
        planner.expandField(extraTicks = 200.0, maxExpansions = 60_000)

        PlanningDebugChannel.publishGraph(planner, Vec3d(0.5, 64.0, 0.5))
        val sample = assertNotNull(PlanningDebugChannel.graph, "an active channel must publish a graph")

        assertEquals(planner.graphNodes.size, sample.total, "the total must be the whole graph, not the sample")
        assertTrue(sample.nodes.isNotEmpty(), "a converged search must have touched something")
        assertTrue(
            sample.nodes.size <= sample.total,
            "the sample cannot exceed the graph it came from",
        )
        assertTrue(
            sample.nodes.all { it.pos.squaredDistanceTo(Vec3d(0.5, 64.0, 0.5)) <= 48.0 * 48.0 + 1e-6 },
            "every drawn cell must lie inside the view radius",
        )
        assertTrue(
            sample.cheapest <= sample.dearest,
            "the cost range must be ordered: ${sample.cheapest}..${sample.dearest}",
        )
    }

    /**
     * The edges are the half of the view that says how cells connect.
     *
     * Cells alone can only ever show *that* the search reached somewhere. A cell sitting
     * next to a route with no edge into it is the picture of a move that does not exist --
     * which was exactly the ladder-descent bug -- and no amount of shading shows it.
     */
    @Test
    fun `the graph view publishes the edges between the cells it drew`() {
        PlanningDebugChannel.begin(true)
        val planner = plannerOverFlatGround()
        planner.repair(Duration.INFINITE)
        planner.expandField(extraTicks = 200.0, maxExpansions = 60_000)

        PlanningDebugChannel.publishGraph(planner, Vec3d(0.5, 64.0, 0.5))
        val sample = assertNotNull(PlanningDebugChannel.graph)

        assertTrue(sample.edges.isNotEmpty(), "a converged search over open ground has expanded moves")
        assertTrue(
            sample.edges.size <= sample.totalEdges,
            "the drawn edges cannot exceed the edges the search holds between drawn cells",
        )

        // An endpoint with no cell would be drawn as a line running off into nothing.
        val drawn = sample.nodes.map { it.pos }.toHashSet()
        assertTrue(
            sample.edges.all { it.from in drawn && it.to in drawn },
            "every edge must join two drawn cells",
        )

        // A move reaches a nearby stance; the longest is a descending jump at about five
        // blocks. Anything beyond that is the optimistic step into unstreamed terrain, which
        // is a fiction the search uses rather than a move the body could make -- drawn, it
        // fires a line at the goal from every frontier cell. The bound only has to separate
        // those two populations, and they are tens of blocks apart.
        assertTrue(
            sample.edges.all { it.from.distanceTo(it.to) <= MAX_REAL_MOVE_BLOCKS },
            "no drawn edge may be an optimistic jump to the goal",
        )
    }

    /** The picked edge has to actually be the pick, or the flow field is decoration. */
    @Test
    fun `each cell picks its cheapest way onward exactly once`() {
        PlanningDebugChannel.begin(true)
        val planner = plannerOverFlatGround()
        planner.repair(Duration.INFINITE)
        planner.expandField(extraTicks = 200.0, maxExpansions = 60_000)

        PlanningDebugChannel.publishGraph(planner, Vec3d(0.5, 64.0, 0.5))
        val sample = assertNotNull(PlanningDebugChannel.graph)

        sample.edges.groupBy { it.from }.forEach { (from, outgoing) ->
            val policy = outgoing.filter { it.policy }
            assertTrue(policy.size <= 1, "cell $from picked ${policy.size} ways onward")

            val picked = policy.firstOrNull() ?: return@forEach
            val cheapest = outgoing.minOf { it.cost + it.toCost }
            assertEquals(
                cheapest,
                picked.cost + picked.toCost,
                1e-9,
                "cell $from did not pick its cheapest way onward",
            )
        }

        assertTrue(
            sample.edges.any { it.policy },
            "a search that converged must have picked a way onward somewhere",
        )
    }

    /**
     * The budgets are the view's contract with the render thread, so they are settings.
     *
     * A radius and a cell count that only exist as constants cannot be raised to look
     * further at a graph that failed, and cannot be lowered when a long path is costing
     * frames. Asserting a *tightened* budget is what proves they are wired through rather
     * than merely declared.
     */
    @Test
    fun `the graph view honours the budgets it was given`() {
        val limits = PlanningDebugChannel.GraphViewLimits(radius = 6.0, cells = 12, edges = 5)
        PlanningDebugChannel.begin(true, limits)
        val planner = plannerOverFlatGround()
        planner.repair(Duration.INFINITE)
        planner.expandField(extraTicks = 200.0, maxExpansions = 60_000)

        val around = Vec3d(0.5, 64.0, 0.5)
        PlanningDebugChannel.publishGraph(planner, around)
        val sample = assertNotNull(PlanningDebugChannel.graph)

        assertTrue(sample.nodes.size <= limits.cells, "drew ${sample.nodes.size} cells for ${limits.cells}")
        assertTrue(sample.edges.size <= limits.edges, "drew ${sample.edges.size} edges for ${limits.edges}")
        assertTrue(
            sample.nodes.all { it.pos.squaredDistanceTo(around) <= limits.radius * limits.radius + 1e-6 },
            "a cell was drawn outside the ${limits.radius} block radius",
        )
        assertTrue(
            sample.total > sample.nodes.size,
            "the total must still report the whole graph the sample came from",
        )
    }

    /** Nothing is published while the channel is off, so the render cannot show a stale field. */
    @Test
    fun `an inactive channel publishes no graph`() {
        PlanningDebugChannel.begin(false)
        val planner = plannerOverFlatGround()
        planner.repair(Duration.INFINITE)

        PlanningDebugChannel.publishGraph(planner, Vec3d(0.5, 64.0, 0.5))
        assertEquals(null, PlanningDebugChannel.graph)
    }

    /** Comfortably past the longest real move, and far short of a goal-spanning edge. */
    private val MAX_REAL_MOVE_BLOCKS = 8.0

    private fun plannerOverFlatGround(): CoarsePlanner {
        val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
        for (x in -40..40) for (z in -40..40) blocks[BlockPos(x, 63, z)] = SnapshotBlockPhysics.FULL_CUBE
        val environment = SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-48, 55, -48, 48, 80, 48), blocks,
        )
        return CoarsePlanner(
            environment,
            SimpleMoveLibrary.build(costs = CoarseMoveCosts.measured(transitionOverheadTicks = 1.0), options = SimpleMoveOptions()),
            Stance(0, 64, 0),
            Stance(30, 64, 30),
        )
    }
}
