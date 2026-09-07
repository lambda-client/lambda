/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pathing

import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.coarse.CoarsePlanningState
import com.lambda.pathing.core.Stance
import com.lambda.pathing.session.RouteResolution
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import net.minecraft.util.math.BlockPos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration

/**
 * Rough open terrain, the field case the corridor corpus never covers (68 edges per stance).
 * Guards coarse determinism and cost there; the timing line is informational.
 */
class CoarseOpenFloorSpeedTest {
    private fun height(x: Int, z: Int): Int {
        var h = x * 374761393 + z * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        return (h ushr 28) % 3
    }

    @Test
    fun `coarse cost on a rough open floor`() {
        val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
        for (x in -180..-60) for (z in -110..0) for (y in 0..(3 + height(x, z))) blocks[BlockPos(x, y, z)] = SnapshotBlockPhysics.FULL_CUBE
        val environment = SnapshotSimulationEnvironment.synthetic(
            bounds = SimulationSnapshotBounds(-190, -8, -120, -50, 40, 10), blocks = blocks,
        )
        val start = Stance(-82, 4 + height(-82, -91), -91)
        val goal = Stance(-164, 4 + height(-164, -13), -13)
        repeat(3) { round ->
            val state = CoarsePlanningState(environment, SimpleMoveOptions(), start, goal, horizonChunks = 0)
            state.repairFrom(start, emptySet(), emptySet())
            val t0 = System.nanoTime()
            val coarse = state.planner.repair(Duration.INFINITE, maxExpansions = 1_000_000)
            val ms = (System.nanoTime() - t0) / 1e6
            val route = RouteResolution(state).resolve(start, 1L, 1_000_000, world = null)
            println("[speed] round=$round expansions=${coarse.processedNodes} nodes=${state.planner.graphSize} ms=%.1f us/exp=%.1f route=${route?.nodes?.size} ticks=${route?.lowerBoundTicks}".format(ms, ms * 1000.0 / coarse.processedNodes))
            assertNotNull(route)
            // Pinned against the pre-refactor planner on this exact floor: same expansions, same bound.
            assertEquals(5342, coarse.processedNodes, "coarse expansions on the open floor")
            assertEquals(378.47077731779956, route.lowerBoundTicks, 1e-6, "route lower bound on the open floor")
        }
    }
}
