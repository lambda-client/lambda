/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.neural

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.util.player.prediction.MovementSimulationInput
import java.nio.FloatBuffer
import java.nio.file.Files
import java.nio.file.Path

/**
 * Bridge-free inference for the ONNX-exported movement policy.
 *
 * The exported graph bakes in Sample Factory's input normalisation, so the caller
 * passes the exact observation the RL environment builds (see the block-field / D*
 * terrain envs) and receives the five discrete action components. Input scaling and
 * argmax match the training environment's decoding bit for bit; a mismatch there is
 * the silent divergence to check first if live behaviour looks wrong.
 *
 * Not thread-safe: one instance owns one [OrtSession]; use one per pathing worker.
 */
class NeuralMovementPolicy private constructor(modelBytes: ByteArray) : AutoCloseable {
    private val environment: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession = environment.createSession(modelBytes, OrtSession.SessionOptions())

    /** Greedy action `[forward, strafe, jump, sprint, yawDelta]` for one observation. */
    fun act(observation: FloatArray): IntArray = argmaxHeads(logits(observation))

    /**
     * A stochastic action sampled per head at [temperature]. Used to escape a stalled
     * greedy rollout: resampling near an obstacle is what makes several proposals clear
     * a jump the single greedy line could not.
     */
    fun sample(observation: FloatArray, temperature: Double, random: java.util.Random): IntArray =
        sampleHeads(logits(observation), temperature, random)

    /** Raw 17 action logits (3+3+2+2+7) for one observation. */
    fun logits(observation: FloatArray): FloatArray {
        require(observation.size == OBSERVATION_SIZE) {
            "Policy expects $OBSERVATION_SIZE observation floats, got ${observation.size}"
        }
        val tensor = OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(observation),
            longArrayOf(1, OBSERVATION_SIZE.toLong()),
        )
        tensor.use {
            session.run(mapOf(INPUT_NAME to it)).use { result ->
                @Suppress("UNCHECKED_CAST")
                return (result[0].value as Array<FloatArray>)[0]
            }
        }
    }

    /**
     * The movement input for [act]'s output at the given movement yaw. Mirrors the
     * training environment's `step`: categorical axes map through [AXIS_ACTIONS] and
     * the yaw head is a per-tick delta from [YAW_DELTAS] applied to [movementYaw].
     */
    fun inputFor(action: IntArray, movementYaw: Double): MovementSimulationInput {
        require(action.size == ACTION_SIZES.size) { "Expected ${ACTION_SIZES.size} action components" }
        return MovementSimulationInput(
            forward = AXIS_ACTIONS[action[0]],
            strafe = AXIS_ACTIONS[action[1]],
            jump = action[2] == 1,
            sprint = action[3] == 1,
            rotation = Rotation(movementYaw + YAW_DELTAS[action[4]], 0.0),
        )
    }

    private fun argmaxHeads(logits: FloatArray): IntArray {
        val action = IntArray(ACTION_SIZES.size)
        var offset = 0
        for (head in ACTION_SIZES.indices) {
            var best = 0
            var bestValue = logits[offset]
            for (choice in 1 until ACTION_SIZES[head]) {
                if (logits[offset + choice] > bestValue) {
                    bestValue = logits[offset + choice]
                    best = choice
                }
            }
            action[head] = best
            offset += ACTION_SIZES[head]
        }
        return action
    }

    private fun sampleHeads(logits: FloatArray, temperature: Double, random: java.util.Random): IntArray {
        val action = IntArray(ACTION_SIZES.size)
        var offset = 0
        for (head in ACTION_SIZES.indices) {
            val size = ACTION_SIZES[head]
            var max = Double.NEGATIVE_INFINITY
            for (choice in 0 until size) max = maxOf(max, logits[offset + choice].toDouble())
            var sum = 0.0
            val weights = DoubleArray(size)
            for (choice in 0 until size) {
                val w = kotlin.math.exp((logits[offset + choice] - max) / temperature)
                weights[choice] = w
                sum += w
            }
            var roll = random.nextDouble() * sum
            var picked = size - 1
            for (choice in 0 until size) {
                roll -= weights[choice]
                if (roll <= 0.0) {
                    picked = choice
                    break
                }
            }
            action[head] = picked
            offset += size
        }
        return action
    }

    override fun close() {
        session.close()
    }

    companion object {
        /** Dense value-layout observation width; must match the exporting environment. */
        const val OBSERVATION_SIZE = 2055
        private const val INPUT_NAME = "obs"
        private val ACTION_SIZES = intArrayOf(3, 3, 2, 2, 7)
        private val AXIS_ACTIONS = doubleArrayOf(-1.0, 0.0, 1.0)
        private val YAW_DELTAS = doubleArrayOf(-15.0, -10.0, -5.0, 0.0, 5.0, 10.0, 15.0)

        fun fromBytes(modelBytes: ByteArray): NeuralMovementPolicy = NeuralMovementPolicy(modelBytes)

        fun fromFile(path: Path): NeuralMovementPolicy = NeuralMovementPolicy(Files.readAllBytes(path))
    }
}
