/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pathing

import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.core.CellBox
import com.lambda.pathing.core.Stance
import com.lambda.pathing.world.CoarseVoxel
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.snapshot.ImmutableSnapshotSection
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import net.minecraft.util.math.ChunkSectionPos
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import pathing.ProbeScenarios.moveLibrary

/**
 * Terrain the planner has not captured is open ground to it, whether or not the client
 * has the chunk loaded: optimism is the same everywhere, and capture resolves it along
 * the route. Treating loaded-but-uncaptured terrain as a wall made stale frontier
 * behind the body beat the uncaptured way ahead -- a journey to (1M, 1M) restarted at
 * (14521, 70, 14291) routed 360 blocks back to (14271, 62, 14032). The rule it replaced
 * guarded against a cold-start random walk caused by partial sweeps picking arbitrary
 * anchors; with the frontier exact, optimism points at the goal.
 */
class UncapturedFrontierTest {
    private fun island(): SnapshotSimulationEnvironment {
        val floor = ImmutableSnapshotSection.Builder().apply {
            for (x in 0..15) for (z in 0..15) for (y in 0..15) {
                set(x, y, z, if (y <= 4) SnapshotBlockPhysics.FULL_CUBE else SnapshotBlockPhysics.AIR)
            }
        }.build(expectedWrites = 4096)
        return streamedSnapshot(SimulationSnapshotBounds(-256, 0, -256, 255, 15, 255), mapOf(ChunkSectionPos.asLong(0, 0, 0) to floor))
    }

    @Test
    fun `a body still being captured heads for the goal`() {
        for (goal in listOf(Stance(200, 5, 1), Stance(-200, 5, 1), Stance(1, 5, 200), Stance(200, 5, 200))) {
            val planner = CoarsePlanner(island(), moveLibrary(), Stance(8, 5, 8), goal)
            planner.repair(maxExpansions = 100_000)
            val route = assertNotNull(planner.routePlan(snapshotRevision = 1L), "uncaptured surroundings are open, not walls: $goal")
            val end = route.goal
            assertTrue((end.x - 8) * (goal.x - 8) + (end.z - 8) * (goal.z - 8) > 0, "the route toward $goal must lead toward it, ended at $end")
            assertTrue(end.x == 0 || end.x == 15 || end.z == 0 || end.z == 15, "the route must end at the edge of what is captured: $end")
        }
    }

    @Test
    fun `an anchor retires once its unknown is known`() {
        var knownMaxX = 15
        val view = object : CoarseVoxelView {
            override val simulableStanceY = 0..15

            override fun isKnown(x: Int, y: Int, z: Int): Boolean = x <= knownMaxX

            override fun voxel(x: Int, y: Int, z: Int): CoarseVoxel = when {
                x > knownMaxX -> CoarseVoxel.UNKNOWN
                y < 3 -> CoarseVoxel.FULL_BLOCK
                else -> CoarseVoxel.AIR
            }
        }
        val planner = CoarsePlanner(view, moveLibrary(), Stance(1, 3, 1), Stance(200, 3, 1))
        planner.terrainChanged(listOf(CellBox(0..15, 0..15, -16..15)))
        assertTrue(planner.optimisticAnchors.keys.isNotEmpty() && planner.optimisticAnchors.keys.all { it.x == 15 }, "the edge of the known world anchors")

        knownMaxX = 47
        planner.terrainChanged(listOf(CellBox(16..47, 0..15, -16..15)))
        val anchors = planner.optimisticAnchors.keys
        assertTrue(anchors.isNotEmpty() && anchors.all { it.x == 47 }, "known ground retires its anchors and the new edge takes over: $anchors")
    }
}
