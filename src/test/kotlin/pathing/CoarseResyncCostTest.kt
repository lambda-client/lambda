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
import com.lambda.pathing.core.PathingChunk
import com.lambda.pathing.core.Stance
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import net.minecraft.util.math.BlockPos
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** What a chunk-arrival resynchronisation costs once the coarse graph is field-sized. */
class CoarseResyncCostTest {
    private fun height(x: Int, z: Int): Int {
        var h = x * 374761393 + z * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        return (h ushr 28) % 3
    }

    @Test
    fun `chunk arrival resync on a large rough field`() {
        val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
        for (x in -200..-40) for (z in -130..20) for (y in 0..(3 + height(x, z))) blocks[BlockPos(x, y, z)] = SnapshotBlockPhysics.FULL_CUBE
        val environment = SnapshotSimulationEnvironment.synthetic(
            bounds = SimulationSnapshotBounds(-210, -8, -140, -30, 40, 30), blocks = blocks,
        )
        val start = Stance(-82, 4 + height(-82, -91), -91)
        val goal = Stance(-164, 4 + height(-164, -13), -13)
        val state = CoarsePlanningState(environment, SimpleMoveOptions(), start, goal, horizonChunks = 0)
        state.repairFrom(start, emptySet(), emptySet())
        state.planner.repair(Duration.INFINITE, maxExpansions = 1_000_000)
        state.planner.expandField(extraTicks = 400.0, timeBudget = 30.seconds, maxExpansions = 200_000)
        println("[resync] graph nodes=${state.planner.graphSize}")
        for (round in 0 until 3) {
            val chunks = HashSet<PathingChunk>()
            for (cx in -8..-6) for (cz in -4..-2) chunks += PathingChunk(cx, cz)
            val t0 = System.nanoTime()
            val result = state.planner.chunksChanged(chunks)
            val syncMs = (System.nanoTime() - t0) / 1e6
            val t1 = System.nanoTime()
            val repair = state.planner.repair(Duration.INFINITE, maxExpansions = 1_000_000)
            val repairMs = (System.nanoTime() - t1) / 1e6
            println("[resync] round=$round 9 chunks: sync=%.1f ms (%s) repair=%.1f ms (%d expansions)".format(syncMs, result, repairMs, repair.processedNodes))
        }
        // Arrival-only: the same chunks, but only origins that read the unknown regenerate.
        run {
            val chunks = HashSet<PathingChunk>()
            for (cx in -8..-6) for (cz in -4..-2) chunks += PathingChunk(cx, cz)
            val t0 = System.nanoTime()
            val result = state.planner.chunksChanged(chunks, arrivalsOnly = true)
            println("[resync] arrivals-only interior: %.1f ms (%s)".format((System.nanoTime() - t0) / 1e6, result))
        }
        // Sliced: the same arrival paid in bounded calls.
        val chunks = HashSet<PathingChunk>()
        for (cx in -8..-6) for (cz in -4..-2) chunks += PathingChunk(cx, cz)
        val t0 = System.nanoTime()
        state.planner.chunksChanged(chunks, maxStances = 150)
        var calls = 1
        var maxCallMs = (System.nanoTime() - t0) / 1e6
        while (state.planner.pendingSyncSize > 0) {
            val t = System.nanoTime()
            state.planner.continueSync(150)
            maxCallMs = maxOf(maxCallMs, (System.nanoTime() - t) / 1e6)
            calls++
        }
        println("[resync] sliced: $calls calls, longest call %.1f ms, total %.1f ms".format(maxCallMs, (System.nanoTime() - t0) / 1e6))
        kotlin.test.assertTrue(maxCallMs < 100.0, "a sliced sync call must stay short, was $maxCallMs ms")
    }
}
