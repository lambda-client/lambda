/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pathing

import com.lambda.pathing.TrajectoryPlanner
import com.lambda.pathing.coarse.CoarseMoveCosts
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.FrontierAnchors
import com.lambda.pathing.coarse.SimpleMoveLibrary
import com.lambda.pathing.coarse.Stance
import com.lambda.pathing.world.CoarseVoxel
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.PathingChunk
import com.lambda.util.player.prediction.ImmutableSnapshotSection
import com.lambda.util.player.prediction.SimulationSnapshotBounds
import com.lambda.util.player.prediction.SnapshotBlockPhysics
import com.lambda.util.player.prediction.SnapshotSimulationEnvironment
import net.minecraft.util.math.ChunkSectionPos
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration

/**
 * The coarse layer where the client's world runs out.
 *
 * The contract is that unstreamed terrain is *absent*, not assumed: the graph holds
 * only stances the client has, and the goal is reached by one optimistic edge from
 * the streamed frontier that chunk arrivals retire.
 */
class UnloadedCoarsePlannerTest {
    @Test
    fun `a goal in unstreamed terrain is reached only through the optimistic edge`() {
        val unavailableKeys = ConcurrentHashMap.newKeySet<Long>()
        val unavailableSection = ImmutableSnapshotSection.Builder().apply {
            fill(SnapshotBlockPhysics.UNAVAILABLE)
        }.build(expectedWrites = 4096)
        val floor = ImmutableSnapshotSection.Builder().apply {
            for (x in 0..15) for (z in 0..15) for (y in 0..15) {
                set(x, y, z, if (y <= 4) SnapshotBlockPhysics.FULL_CUBE else SnapshotBlockPhysics.AIR)
            }
        }.build(expectedWrites = 4096)
        val streamed = ChunkSectionPos.asLong(0, 0, 0)
        val snapshot = SnapshotSimulationEnvironment(
            bounds = SimulationSnapshotBounds(0, 0, 0, 255, 15, 255),
            sections = mapOf(streamed to floor),
            defaultBlock = null,
            missingSection = { sectionX, sectionY, sectionZ, exact ->
                check(!exact) { "exact simulation must never consume unstreamed terrain" }
                unavailableKeys += ChunkSectionPos.asLong(sectionX, sectionY, sectionZ)
                unavailableSection
            },
            unavailableSectionKeys = unavailableKeys,
        )
        val start = Stance(1, 5, 1)
        val goal = Stance(200, 5, 1)
        val moves = SimpleMoveLibrary.build(CoarseMoveCosts.measured(transitionOverheadTicks = 1.0))
        val planner = CoarsePlanner(TrajectoryPlanner.coarseView(snapshot), moves, start, goal)

        planner.advanceFrontier(FrontierAnchors.probe(snapshot, moves, start, goal))
        val result = planner.repair(Duration.INFINITE, maxExpansions = 100_000)
        val route = assertNotNull(planner.routePlan(snapshotRevision = 1L))

        assertTrue(result.converged)
        // Bounded by the streamed chunk, not by an invented surface stretching to the goal.
        assertTrue(result.processedNodes < 2_000, "searched ${result.processedNodes} nodes")
        assertEquals(start, route.nodes.first())
        assertTrue(route.goal != goal, "the goal is not streamed and cannot be published as reached")
        route.nodes.forEach { node ->
            assertTrue(snapshot.isKnown(node.x, node.y, node.z), "$node is not streamed")
        }
        assertTrue(route.goal.x > start.x, "the route must head at the goal, ended at ${route.goal}")
    }

    @Test
    fun `a goal walled off by streamed terrain is refused`() {
        val view = object : CoarseVoxelView {
            override val simulableStanceY = 0..20

            override fun voxel(x: Int, y: Int, z: Int): CoarseVoxel = when {
                x !in -8..8 || z !in 0..40 -> CoarseVoxel.HAZARD
                z == 20 -> CoarseVoxel.FULL_BLOCK
                y < 3 -> CoarseVoxel.FULL_BLOCK
                else -> CoarseVoxel.AIR
            }
        }
        val planner = CoarsePlanner(
            view,
            SimpleMoveLibrary.build(CoarseMoveCosts.measured(transitionOverheadTicks = 1.0)),
            Stance(0, 3, 0),
            Stance(0, 3, 40),
        )
        planner.repair(Duration.INFINITE, maxExpansions = 100_000)

        // Streamed terrain is not optimism: what it says is blocked is blocked, and no
        // amount of further searching turns that into a route.
        assertNull(planner.routePlan(snapshotRevision = 1L))
        assertNull(planner.resynchronizedRoutePlan(snapshotRevision = 1L))
        assertTrue(planner.optimisticAnchors.isEmpty(), "streamed terrain needs no optimistic edge")
    }

    @Test
    fun `a route whose terrain changed unnoticed is rebuilt rather than refused`() {
        var walled = false
        val view = object : CoarseVoxelView {
            override val simulableStanceY = 0..20

            override fun voxel(x: Int, y: Int, z: Int): CoarseVoxel = when {
                x !in -8..8 || z !in 0..40 -> CoarseVoxel.HAZARD
                walled && z == 20 && x == 0 -> CoarseVoxel.FULL_BLOCK
                y < 3 -> CoarseVoxel.FULL_BLOCK
                else -> CoarseVoxel.AIR
            }
        }
        val planner = CoarsePlanner(
            view,
            SimpleMoveLibrary.build(CoarseMoveCosts.measured(transitionOverheadTicks = 1.0)),
            Stance(0, 3, 0),
            Stance(0, 3, 40),
        )
        assertTrue(planner.repair(Duration.INFINITE, maxExpansions = 100_000).converged)
        val straight = assertNotNull(planner.routePlan(snapshotRevision = 1L))
        assertTrue(straight.nodes.any { it.x == 0 && it.z == 20 })

        // The world changed under the cached edges without anyone reporting it.
        walled = true

        val rebuilt = assertNotNull(
            planner.resynchronizedRoutePlan(snapshotRevision = 2L),
            "publication must not depend on cached edges agreeing with the world",
        )
        assertEquals(Stance(0, 3, 40), rebuilt.goal)
        assertTrue(rebuilt.nodes.none { it.x == 0 && it.z == 20 })
    }

    @Test
    fun `chunk arrivals retire the optimistic edge they replace`() {
        val world = HashSet<PathingChunk>()
        val view = object : CoarseVoxelView {
            override val simulableStanceY = 0..20

            override fun isKnown(x: Int, y: Int, z: Int) = PathingChunk(x shr 4, z shr 4) in world

            override fun voxel(x: Int, y: Int, z: Int): CoarseVoxel = when {
                !isKnown(x, y, z) -> CoarseVoxel.UNKNOWN
                y < 3 -> CoarseVoxel.FULL_BLOCK
                else -> CoarseVoxel.AIR
            }
        }
        for (x in 0..1) for (z in -1..1) world += PathingChunk(x, z)
        val moves = SimpleMoveLibrary.build(CoarseMoveCosts.measured(transitionOverheadTicks = 1.0))
        val start = Stance(0, 3, 0)
        val goal = Stance(120, 3, 0)
        val planner = CoarsePlanner(view, moves, start, goal)

        planner.advanceFrontier(FrontierAnchors.probe(view, moves, start, goal))
        planner.repair(Duration.INFINITE, maxExpansions = 100_000)
        val before = planner.optimisticAnchors
        assertTrue(before.isNotEmpty())

        for (z in -1..1) world += PathingChunk(2, z)
        planner.chunksChanged((-1..1).map { PathingChunk(2, it) })
        planner.advanceFrontier(FrontierAnchors.probe(view, moves, start, goal))
        planner.repair(Duration.INFINITE, maxExpansions = 100_000)
        val after = planner.optimisticAnchors

        assertTrue(after.values.min() < before.values.min(), "the optimistic remainder must shrink")
        (before.keys - after.keys).forEach {
            assertEquals(Double.POSITIVE_INFINITY, planner.optimisticEdgeCost(it))
        }
        val route = assertNotNull(planner.routePlan(snapshotRevision = 2L))
        assertTrue(route.goal.x > 32, "the graph must advance with the world, ended at ${route.goal}")
    }
}
