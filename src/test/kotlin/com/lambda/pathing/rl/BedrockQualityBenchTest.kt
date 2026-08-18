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
import com.lambda.pathing.trajectory.WalkingSeedSearch
import com.lambda.pathing.trajectory.WalkingSeedSearchConfig
import com.lambda.pathing.trajectory.WalkingSeedSearchResult
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration

/**
 * The search side of the learned-vs-search **trajectory quality** head-to-head:
 *
 *     ./gradlew bedrockQuality
 *
 * For each shared probe seed, [BedrockFieldEnvironment.segmentSetup] fixes a start
 * state and a start/goal stance a few coarse edges apart. Here the non-learned
 * [WalkingSeedSearch] plans and certifies a door-to-door trajectory from that exact
 * start state; the policy is driven over the RL bridge from the identical seed. Both
 * brake to a stop at the same goal, so ticks and collisions compare directly.
 *
 * Quality, not reachability, is the point: total ticks (time — the true objective) and
 * horizontal-collision frames (reported, never rewarded — a collision is only bad when
 * it is slow, which ticks already price).
 */
@Tag("bedrock-quality")
class BedrockQualityBenchTest {
    @Test
    fun `search produces a door-to-door quality report over shared probes`() {
        val moves = SimpleMoveLibrary.build(
            costs = CoarseMoveCosts.measured(transitionOverheadTicks = 1.0),
            options = SimpleMoveOptions(),
        )
        val environment = BedrockFieldEnvironment.ENVIRONMENT
        val report = ArrayList<String>()
        report += "seed,jumps,result,ticks,collisions,goalError,coarseMillis,searchMillis"

        for (seed in 0 until PROBES) {
            val setup = BedrockFieldEnvironment.segmentSetup(seed.toLong())
            val coarseStarted = System.nanoTime()
            val planner = CoarsePlanner(environment, moves, setup.startStance, setup.goalStance)
            if (!planner.repair(Duration.INFINITE).converged) {
                report += "$seed,${setup.jumpCount},NO_ROUTE,0,0,,${elapsedMs(coarseStarted)},0"
                continue
            }
            val plan = planner.routePlan(snapshotRevision = seed.toLong())
            if (plan == null) {
                report += "$seed,${setup.jumpCount},NO_PLAN,0,0,,${elapsedMs(coarseStarted)},0"
                continue
            }
            val coarseMillis = elapsedMs(coarseStarted)

            val searchStarted = System.nanoTime()
            val result = WalkingSeedSearch.searchContinuously(
                route = plan,
                initialState = setup.startState,
                profile = BedrockFieldEnvironment.PROFILE,
                environment = environment,
                config = WalkingSeedSearchConfig(),
            )
            val searchMillis = elapsedMs(searchStarted)

            val row = when (result) {
                is WalkingSeedSearchResult.Success -> {
                    val frames = result.rollout.frames
                    val collisions = frames.count { it.state.horizontalCollision }
                    val goalError = distance(
                        frames.lastOrNull()?.state?.position,
                        setup.goalStance,
                    )
                    "Success,${frames.size},$collisions,${"%.3f".format(goalError)}"
                }
                else -> "${result::class.simpleName},0,0,"
            }
            report += "$seed,${setup.jumpCount},$row,$coarseMillis,$searchMillis"
        }

        val path = Path.of("build/reports/pathing/bedrock-quality-search.csv")
        Files.createDirectories(path.parent)
        Files.write(path, report)
        report.forEach { println("[bedrock-quality] $it") }
    }

    private fun distance(position: net.minecraft.util.math.Vec3d?, goal: com.lambda.pathing.coarse.Stance): Double {
        if (position == null) return Double.NaN
        val dx = position.x - (goal.x + 0.5)
        val dy = position.y - goal.y
        val dz = position.z - (goal.z + 0.5)
        return kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun elapsedMs(startNanos: Long): Long = (System.nanoTime() - startNanos) / 1_000_000

    private companion object {
        const val PROBES = 300
    }
}
