/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.rl

import com.lambda.pathing.coarse.CoarseMoveCosts
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.SimpleMoveLibrary
import com.lambda.pathing.coarse.SimpleMoveOptions
import com.lambda.pathing.coarse.Stance
import com.lambda.pathing.debug.BedrockFieldLayout
import com.lambda.util.player.prediction.SimulationSnapshotBounds
import com.lambda.util.player.prediction.SnapshotBlockPhysics
import com.lambda.util.player.prediction.SnapshotSimulationEnvironment
import net.minecraft.util.math.BlockPos
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Duration

/**
 * De-risk: is a fresh D* plan per training episode cheap enough to be an oracle
 * reward source? Times [CoarsePlanner] on episode-sized (~[PATCH] block) randomised
 * bedrock patches. A V-reward training env plans once per reset and reads
 * `tailLowerBound` per tick, so this planning cost is the whole throughput risk.
 *
 *     ./gradlew dstarProbe
 */
@Tag("dstar-probe")
class DStarThroughputProbeTest {
    @Test
    fun `coarse planning on episode-sized patches is fast enough for per-episode use`() {
        val moves = SimpleMoveLibrary.build(
            costs = CoarseMoveCosts.measured(transitionOverheadTicks = 1.0),
            options = SimpleMoveOptions(),
        )
        val millis = ArrayList<Long>()
        var converged = 0
        var attempts = 0
        for (seed in 0 until PROBES) {
            val cells = BedrockFieldLayout.solidCells(length = PATCH, halfWidth = HALF, seed = seed)
            val surface = BedrockFieldLayout.standableSurface(cells, PATCH, HALF)
            if (surface.size < 2) continue
            val pairs = BedrockFieldLayout.randomEndpointPairs(
                count = 1,
                length = PATCH,
                halfWidth = HALF,
                terrainSeed = seed,
                pairSeed = seed xor 0x9E37,
                minHorizontalDistance = PATCH / 3.0,
            )
            val (from, to) = pairs.first()
            val environment = SnapshotSimulationEnvironment.synthetic(
                bounds = SimulationSnapshotBounds(-2, 56, -HALF - 2, PATCH + 1, 71, HALF + 2),
                blocks = cells.associate { BlockPos(it.x, it.y, it.z) to SnapshotBlockPhysics.FULL_CUBE },
            )
            val planner = CoarsePlanner(
                environment, moves,
                Stance(from.x, from.y, from.z),
                Stance(to.x, to.y, to.z),
            )
            attempts++
            val started = System.nanoTime()
            val result = planner.repair(Duration.INFINITE)
            millis += (System.nanoTime() - started) / 1_000_000
            if (result.converged) converged++
        }
        millis.sort()
        val mean = millis.average()
        val p50 = millis[millis.size / 2]
        val p95 = millis[(millis.size * 95) / 100]
        val estimatedTicksPerSecond = 1000.0 / (mean / ESTIMATED_TICKS_PER_EPISODE + ESTIMATED_MS_PER_TICK)
        println(
            "[dstar-probe] patches=$attempts converged=$converged " +
                "planMs mean=${"%.1f".format(mean)} p50=$p50 p95=$p95 | " +
                "est throughput with per-episode plan ~${estimatedTicksPerSecond.toInt()} ticks/s",
        )
    }

    private companion object {
        const val PROBES = 120
        const val PATCH = 48
        const val HALF = 8
        const val ESTIMATED_TICKS_PER_EPISODE = 150.0
        const val ESTIMATED_MS_PER_TICK = 0.3
    }
}
