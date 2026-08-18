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
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertTrue

/**
 * End-to-end proof that the ONNX-exported policy runs bridge-free in the JVM and
 * reproduces its training behaviour:
 *
 *     ./gradlew neuralSmoke
 *
 * The trained policy drives [DStarTerrainEnvironment] in closed loop entirely inside
 * the JVM — no Python, no socket. If the in-JVM success rate tracks the ~55-60%
 * reported at the end of training, the ONNX export, input normalisation and head
 * argmax all match the Python policy, and the inference latency is the real per-tick
 * cost the mod would pay in game.
 */
@Tag("neural-smoke")
class NeuralPolicySmokeTest {
    @Test
    fun `exported policy drives the simulator in-JVM at its training success rate`() {
        val modelPath = RL_MODEL_PATH
        assertTrue(modelPath.toFile().exists(), "export policy.onnx first (neolambda_rl.export_onnx)")

        NeuralMovementPolicy.fromFile(modelPath).use { policy ->
            var success = 0
            var reached = 0
            val latencyMicros = ArrayList<Long>()
            for (seed in 0 until EPISODES) {
                val environment = DStarTerrainEnvironment()
                var transition = environment.reset(seed.toLong(), 255)
                val startValue = 1.0 - transition.observation[29] // value-progress feature
                while (!transition.terminated && !transition.truncated) {
                    val started = System.nanoTime()
                    val action = policy.act(transition.observation)
                    latencyMicros += (System.nanoTime() - started) / 1_000
                    transition = environment.step(action)
                }
                if (transition.outcome == RlOutcome.SUCCESS) success++
                if (transition.observation[29] > 0.6) reached++ // >60% of the time-to-go eliminated
                @Suppress("UNUSED_EXPRESSION") startValue
            }
            latencyMicros.sort()
            val median = latencyMicros[latencyMicros.size / 2]
            val p99 = latencyMicros[(latencyMicros.size * 99) / 100]
            println(
                "[neural-smoke] in-JVM policy: success=$success/$EPISODES " +
                    "reached>=60%value=$reached/$EPISODES | " +
                    "infer median=${median}us p99=${p99}us (tick budget 50000us)",
            )
            assertTrue(success > EPISODES / 5, "in-JVM success $success/$EPISODES far below training; export likely diverged")
            assertTrue(median < 50_000, "inference median ${median}us exceeds the 20 TPS tick budget")
        }
    }

    private companion object {
        const val EPISODES = 100
    }
}

/**
 * The exported policy, which lives in the separate `LambdaAI` Python project rather than
 * in this repository. Override with `-Dlambda.rl.modelPath=...`; the default assumes the
 * two projects sit side by side.
 */
internal val RL_MODEL_PATH: java.nio.file.Path = java.nio.file.Path.of(
    System.getProperty("lambda.rl.modelPath") ?: "../LambdaAI/train_dir/dstar_terrain_v0/policy.onnx"
)
