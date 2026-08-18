/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.rl

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.coarse.CoarseMoveCosts
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.SimpleMoveLibrary
import com.lambda.pathing.coarse.SimpleMoveOptions
import com.lambda.pathing.coarse.Stance
import com.lambda.pathing.debug.BedrockFieldLayout
import com.lambda.pathing.neural.NeuralMovementPolicy
import com.lambda.pathing.neural.NeuralTrajectoryDiscovery
import com.lambda.pathing.trajectory.WalkingSeedSearchResult
import com.lambda.util.player.prediction.MovementSimulationState
import com.lambda.util.player.prediction.SimulationSnapshotBounds
import com.lambda.util.player.prediction.SnapshotBlockPhysics
import com.lambda.util.player.prediction.SnapshotSimulationEnvironment
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.math.atan2
import kotlin.time.Duration

/**
 * Reproduces the in-game symptom: full route from rest to a distant goal, not a short
 * moving segment.
 *
 *     ./gradlew neuralRepro
 */
@Tag("neural-repro")
class NeuralFullRouteReproTest {
    @Test
    fun `neural discovery on full corpus routes`() {
        val policy = NeuralMovementPolicy.fromFile(RL_MODEL_PATH)
        val cells = BedrockFieldLayout.solidCells()
        val environment = SnapshotSimulationEnvironment.synthetic(
            bounds = SimulationSnapshotBounds(
                -2, 56, -BedrockFieldLayout.HALF_WIDTH - 2,
                BedrockFieldLayout.LENGTH + 1, 71, BedrockFieldLayout.HALF_WIDTH + 2,
            ),
            blocks = cells.associate { BlockPos(it.x, it.y, it.z) to SnapshotBlockPhysics.FULL_CUBE },
        )
        val moves = SimpleMoveLibrary.build(CoarseMoveCosts.measured(transitionOverheadTicks = 1.0), SimpleMoveOptions())

        policy.use {
            for ((index, pair) in BedrockFieldLayout.randomEndpointPairs(count = 12).withIndex()) {
                val start = Stance(pair.first.x, pair.first.y, pair.first.z)
                val goal = Stance(pair.second.x, pair.second.y, pair.second.z)
                val planner = CoarsePlanner(environment, moves, start, goal)
                if (!planner.repair(Duration.INFINITE).converged) continue
                val plan = planner.routePlan(index.toLong()) ?: continue

                val dx = (goal.x - start.x).toDouble()
                val dz = (goal.z - start.z).toDouble()
                val initial = MovementSimulationState.synthetic(
                    profile = BedrockFieldEnvironment.PROFILE,
                    position = Vec3d(start.x + 0.5, start.y.toDouble(), start.z + 0.5),
                    rotation = Rotation(Math.toDegrees(atan2(-dx, dz)), 0.0),
                    velocity = Vec3d(0.0, -0.0784, 0.0),
                )
                val routeBlocks = plan.lowerBoundTicks
                val goalFeatureX = ((goal.x + 0.5 - initial.position.x) / 48.0)

                val result = NeuralTrajectoryDiscovery.search(
                    route = plan, initialState = initial, profile = BedrockFieldEnvironment.PROFILE,
                    snapshot = environment, policy = policy, maxFrames = 160, goalRadius = 0.9,
                )
                val summary = when (result) {
                    is WalkingSeedSearchResult.Success -> "SUCCESS frames=${result.rollout.frames.size}"
                    is WalkingSeedSearchResult.NoSafeStop ->
                        "NoSafeStop node=${result.blockedProgress}/${plan.nodes.lastIndex} " +
                            "goalErr=${"%.1f".format(result.attempts.firstOrNull()?.finalGoalError ?: -1.0)}"
                    else -> result::class.simpleName ?: "?"
                }
                println(
                    "[neural-repro] case $index: nodes=${plan.nodes.size} routeTicks=${"%.0f".format(routeBlocks)} " +
                        "goalFeatX=${"%.2f".format(goalFeatureX)} -> $summary",
                )
            }
        }
    }
}
