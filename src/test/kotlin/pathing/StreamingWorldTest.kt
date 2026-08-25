/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pathing

import com.lambda.pathing.movement.CoarseMoveCosts
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.coarse.FrontierAnchors
import com.lambda.pathing.coarse.SimpleMoveLibrary
import com.lambda.pathing.core.Stance
import com.lambda.pathing.world.CoarseVoxel
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.core.PathingChunk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration

/**
 * The planner against a world that arrives while it is being walked.
 *
 * Every check here is about the state the coarse layer is left in, not about the
 * shape of a route: the goal sits far outside what the client has, the terrain
 * behind it is revealed only as the body approaches, and D* has to stay a correct
 * incremental search across every one of those arrivals.
 */
class StreamingWorldTest {
    /** A world the client receives one chunk ring at a time. */
    private class StreamingWorld(val radiusChunks: Int = 4) {
        var center = PathingChunk(0, 0)
            private set

        private val delivered = HashSet<PathingChunk>()

        val view = object : CoarseVoxelView {
            override val simulableStanceY = 0..32

            override fun isKnown(x: Int, y: Int, z: Int): Boolean = loaded(x, z)

            override fun voxel(x: Int, y: Int, z: Int): CoarseVoxel = when {
                !loaded(x, z) -> CoarseVoxel.UNKNOWN
                y <= surface(x, z) -> CoarseVoxel.FULL_BLOCK
                else -> CoarseVoxel.AIR
            }
        }

        /** Undulating by one block, so every neighbouring column stays walkable. */
        fun surface(x: Int, z: Int): Int = 4 + (((x shr 4) * 7 + (z shr 4) * 13).mod(2))

        fun stance(x: Int, z: Int) = Stance(x, surface(x, z) + 1, z)

        fun loaded(x: Int, z: Int): Boolean = PathingChunk(x shr 4, z shr 4) in delivered

        /** Recentres the streamed ring on [position] and reports what that delivered. */
        fun stream(position: Stance): Set<PathingChunk> {
            center = PathingChunk(position.x shr 4, position.z shr 4)
            val arrived = HashSet<PathingChunk>()
            for (x in -radiusChunks..radiusChunks) for (z in -radiusChunks..radiusChunks) {
                val chunk = PathingChunk(center.x + x, center.z + z)
                if (delivered.add(chunk)) arrived += chunk
            }
            return arrived
        }
    }

    private class Walk(val world: StreamingWorld, start: Stance, val goal: Stance) {
        val moves = SimpleMoveLibrary.build(CoarseMoveCosts.measured(transitionOverheadTicks = 1.0))
        val planner = CoarsePlanner(world.view, moves, start, goal)
        var position = start
            private set
        var revision = 0L
            private set
        val expansions = ArrayList<Int>()

        /** One planning cycle: deliver chunks, repair, publish. */
        fun cycle(): CoarseRoutePlan {
            val arrived = world.stream(position)
            planner.updateStart(position)
            if (arrived.isNotEmpty()) planner.chunksChanged(arrived)
            planner.advanceFrontier(FrontierAnchors.probe(world.view, moves, position, goal))

            val repair = planner.repair(Duration.INFINITE, maxExpansions = 200_000)
            expansions += repair.processedNodes
            assertTrue(repair.converged, "the repair must converge")

            val route = assertNotNull(planner.routePlan(++revision), "route from $position")
            verify(route)
            return route
        }

        /** Advances the body along the certified prefix of [route]. */
        fun advance(route: CoarseRoutePlan, steps: Int) {
            position = route.nodes[minOf(steps, route.nodes.lastIndex)]
        }

        private fun verify(route: CoarseRoutePlan) {
            assertEquals(position, route.nodes.first(), "a route must start where the body is")
            route.nodes.forEach { node ->
                assertTrue(
                    world.loaded(node.x, node.z),
                    "route node $node stands in terrain the client has not received",
                )
                assertTrue(moves.isStance(world.view, node), "route node $node is not a stance")
            }
            route.edges.forEach { edge ->
                assertTrue(
                    moves.edgesFrom(world.view, edge.from).any { it.to == edge.to },
                    "route edge ${edge.from} -> ${edge.to} cannot be rederived from the world",
                )
            }
            val tail = route.nodes.last()
            assertTrue(
                tail == goal || tail in planner.optimisticAnchors,
                "a route short of the goal must end on the optimistic frontier, ended at $tail",
            )
        }
    }

    @Test
    fun `a goal outside the streamed world is walked to as the world arrives`() {
        val world = StreamingWorld()
        val start = world.stance(0, 0)
        val goal = world.stance(0, 200)
        val walk = Walk(world, start, goal)

        var remaining = walk.moves.heuristic(start, goal)
        var arrived = false
        repeat(MAX_CYCLES) {
            val route = walk.cycle()
            if (route.goal == goal && route.nodes.last() == goal) {
                arrived = true
                return@repeat
            }
            walk.advance(route, STEPS_PER_CYCLE)
            val progress = walk.moves.heuristic(walk.position, goal)
            assertTrue(progress < remaining, "cycle made no progress: $remaining -> $progress")
            remaining = progress
        }

        assertTrue(arrived, "never reached $goal, stalled at ${walk.position}")
    }

    @Test
    fun `chunk arrivals repair the graph instead of re-searching it`() {
        val world = StreamingWorld()
        val walk = Walk(world, world.stance(0, 0), world.stance(0, 200))

        repeat(12) {
            val route = walk.cycle()
            walk.advance(route, STEPS_PER_CYCLE)
        }

        assertTrue(walk.expansions.first() > 0, "the first cycle must actually search")
        assertTrue(
            walk.expansions.drop(1).any { it == 0 },
            "walking without new terrain must cost nothing: ${walk.expansions}",
        )
        assertTrue(
            walk.expansions.max() <= RING_REPAIR_BUDGET,
            "a chunk arrival cost more than a local repair: ${walk.expansions}",
        )
    }

    @Test
    fun `the optimistic edge follows the streamed frontier toward the goal`() {
        val world = StreamingWorld()
        val goal = world.stance(0, 200)
        val walk = Walk(world, world.stance(0, 0), goal)

        val route = walk.cycle()
        val firstAnchors = walk.planner.optimisticAnchors
        assertTrue(firstAnchors.isNotEmpty(), "an unstreamed goal needs an optimistic edge")
        firstAnchors.keys.forEach { anchor ->
            assertTrue(world.loaded(anchor.x, anchor.z), "$anchor is not streamed terrain")
            assertTrue(
                (-1..1).any { dx -> (-1..1).any { dz -> !world.loaded(anchor.x + dx, anchor.z + dz) } },
                "$anchor is not at the edge of what is streamed",
            )
        }

        // Far enough to cross into chunks the client had not delivered.
        walk.advance(route, STEPS_PER_CYCLE * 3)
        walk.cycle()
        val advanced = walk.planner.optimisticAnchors

        val before = firstAnchors.values.min()
        val after = advanced.values.min()
        assertTrue(after < before, "the optimistic remainder must shrink: $before -> $after")
        firstAnchors.keys.filterNot { it in advanced }.forEach { retired ->
            assertEquals(
                Double.POSITIVE_INFINITY, walk.planner.optimisticEdgeCost(retired),
                "a retired anchor must not keep its optimistic edge",
            )
        }
    }

    @Test
    fun `a goal inside the streamed world needs no optimistic edge`() {
        val world = StreamingWorld()
        val start = world.stance(0, 0)
        world.stream(start)
        val goal = world.stance(0, 24)
        val walk = Walk(world, start, goal)

        val route = walk.cycle()

        // Anchors may exist past the goal (the frontier is real terrain the fan can
        // reach), but the route through streamed terrain must win on cost and end at
        // the actual goal, never at fiction.
        assertEquals(goal, route.nodes.last())
        route.nodes.forEach { node ->
            assertTrue(node !in walk.planner.optimisticAnchors || node == goal, "route rides fiction at $node")
        }
    }

    @Test
    fun `an unreachable goal in streamed terrain is still refused`() {
        val world = StreamingWorld()
        val start = world.stance(0, 0)
        world.stream(start)
        // A goal in mid-air is a stance nothing can stand on.
        val goal = Stance(0, 20, 8)
        val walk = Walk(world, start, goal)
        walk.planner.updateStart(start)
        walk.planner.advanceFrontier(
            FrontierAnchors.probe(world.view, walk.moves, start, goal),
        )
        walk.planner.repair(Duration.INFINITE, maxExpansions = 50_000)

        assertTrue(
            walk.planner.routePlan(1L)?.nodes?.last() != goal,
            "a goal nothing can stand on must not be published as reached",
        )
    }

    /**
     * A frontier the probe fan cannot reach: an unjumpable wall crosses the entire
     * streamed ring between the body and the goal, ending only far east of anything
     * streamed at the start. Every ray the fan casts lands its anchor beyond the wall,
     * in a component the body cannot enter, so route extraction fails outright -- the
     * exact shape of "walks to the chunk border and then will not move on". The
     * reachability sweep must anchor the frontier the body can actually reach, and the
     * walk must round the wall's end as the world streams in behind it.
     */
    @Test
    fun `a frontier severed from the body is recovered by the reachability sweep`() {
        val wallZ = 30
        val wallEndX = 100
        val world = StreamingWorld()
        val view = object : CoarseVoxelView {
            override val simulableStanceY = 0..32

            override fun isKnown(x: Int, y: Int, z: Int): Boolean = world.loaded(x, z)

            override fun voxel(x: Int, y: Int, z: Int): CoarseVoxel = when {
                !world.loaded(x, z) -> CoarseVoxel.UNKNOWN
                y <= 4 -> CoarseVoxel.FULL_BLOCK
                z == wallZ && x in -wallEndX..wallEndX && y <= 9 -> CoarseVoxel.FULL_BLOCK
                else -> CoarseVoxel.AIR
            }
        }
        val moves = SimpleMoveLibrary.build(CoarseMoveCosts.measured(transitionOverheadTicks = 1.0))
        val start = Stance(0, 5, 0)
        val goal = Stance(0, 5, 200)
        val planner = CoarsePlanner(view, moves, start, goal)

        var position = start
        var revision = 0L
        var sweeps = 0
        var arrived = false
        for (cycle in 0 until MAX_CYCLES) {
            val delivered = world.stream(position)
            planner.updateStart(position)
            if (delivered.isNotEmpty()) planner.chunksChanged(delivered)
            planner.advanceFrontier(FrontierAnchors.probe(view, moves, position, goal))
            planner.repair(Duration.INFINITE, maxExpansions = 500_000)

            var route = planner.routePlan(++revision) ?: planner.resynchronizedRoutePlan(revision)
            if (route == null && planner.discoverReachableFrontier()) {
                sweeps++
                route = planner.routePlan(revision)
            }
            val plan = assertNotNull(route, "stranded with no route at $position on cycle $cycle")
            if (plan.nodes.last() == goal) {
                arrived = true
                break
            }
            position = plan.nodes[minOf(STEPS_PER_CYCLE, plan.nodes.lastIndex)]
        }

        assertTrue(sweeps > 0, "the wall never stranded the fan, so the scenario proves nothing")
        assertTrue(arrived, "never reached $goal, stalled at $position")
    }

    private companion object {
        const val MAX_CYCLES = 80
        const val STEPS_PER_CYCLE = 8

        /** One ring of arrivals is a few thousand stances; a re-search is far more. */
        const val RING_REPAIR_BUDGET = 12_000
    }
}
