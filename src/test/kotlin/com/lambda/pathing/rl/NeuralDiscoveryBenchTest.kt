/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.rl

import com.lambda.pathing.neural.NeuralMovementPolicy
import com.lambda.pathing.neural.NeuralTrajectoryDiscovery
import com.lambda.pathing.trajectory.WalkingSeedSearchResult
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertTrue

/**
 * Validates the production neural discovery path against the known-good bench numbers:
 *
 *     ./gradlew neuralDiscovery
 *
 * [NeuralTrajectoryDiscovery] rebuilds the observation from the live snapshot's `voxel`
 * query (as it will in game), not the training environment's solid set. On this
 * FULL_CUBE terrain the two must agree cell for cell, so if the greedy certification
 * rate here tracks the policy's greedy success on the bridge bench, the main-source
 * observation port matches training and the in-game path is sound. A large gap is an
 * observation-port bug to fix before trusting live behaviour.
 */
@Tag("neural-discovery")
class NeuralDiscoveryBenchTest {
    @Test
    fun `production neural discovery certifies bedrock segments at the greedy rate`() {
        val modelPath = RL_MODEL_PATH
        assertTrue(modelPath.toFile().exists(), "export policy.onnx first (neolambda_rl.export_onnx)")

        NeuralMovementPolicy.fromFile(modelPath).use { policy ->
            var certified = 0
            var ticksTotal = 0
            var collisionsTotal = 0
            for (seed in 0 until PROBES) {
                val setup = BedrockFieldEnvironment.segmentSetup(seed.toLong())
                val result = NeuralTrajectoryDiscovery.search(
                    route = setup.valuePlan,
                    initialState = setup.startState,
                    profile = BedrockFieldEnvironment.PROFILE,
                    snapshot = BedrockFieldEnvironment.ENVIRONMENT,
                    policy = policy,
                    maxFrames = 160,
                    goalRadius = 0.9,
                )
                if (result is WalkingSeedSearchResult.Success) {
                    certified++
                    ticksTotal += result.rollout.frames.size
                    collisionsTotal += result.rollout.frames.count { it.state.horizontalCollision }
                }
            }
            val meanTicks = if (certified > 0) ticksTotal.toDouble() / certified else Double.NaN
            val meanCollisions = if (certified > 0) collisionsTotal.toDouble() / certified else Double.NaN
            println(
                "[neural-discovery] greedy certified=$certified/$PROBES " +
                    "meanTicks=${"%.1f".format(meanTicks)} meanCollisions=${"%.2f".format(meanCollisions)}",
            )
            // Greedy policy solved ~50% of segments on the bridge bench; the production
            // observation must reproduce that, not collapse to near zero.
            assertTrue(
                certified > PROBES * 3 / 10,
                "production discovery certified only $certified/$PROBES; observation likely diverged from training",
            )
        }
    }

    private companion object {
        const val PROBES = 150
    }
}
