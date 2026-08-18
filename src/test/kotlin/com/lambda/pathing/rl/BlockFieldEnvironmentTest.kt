/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.rl

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlockFieldEnvironmentTest {
    @Test
    fun `reset is deterministic and publishes a finite 3d observation`() {
        val first = BlockFieldEnvironment().reset(0xB10CF1E1DL)
        val second = BlockFieldEnvironment().reset(0xB10CF1E1DL)

        assertEquals(BlockFieldEnvironment.OBSERVATION_SIZE, first.observation.size)
        assertContentEquals(first.observation, second.observation)
        assertEquals(first.scenario, second.scenario)
        assertEquals(first.density, second.density)
        assertEquals(RlOutcome.RUNNING, first.outcome)
        assertTrue(first.observation.all(Float::isFinite))
        assertTrue(first.observation[8] > 0.5f, "generated starts must be grounded")
        assertTrue(
            first.observation.drop(BlockFieldEnvironment.DENSE_OBSERVATIONS).any { it > 0.5f },
            "local voxel field must contain terrain",
        )
        assertFalse(first.terminated)
        assertFalse(first.truncated)
    }

    @Test
    fun `seed sweep covers every difficulty and a broad density range`() {
        val difficulties = mutableSetOf<Int>()
        var minimumDensity = 1.0f
        var maximumDensity = 0.0f
        val environment = BlockFieldEnvironment()

        repeat(256) { seed ->
            val transition = environment.reset(seed.toLong())
            difficulties += transition.scenario
            minimumDensity = minOf(minimumDensity, transition.density)
            maximumDensity = maxOf(maximumDensity, transition.density)
        }

        assertEquals((0 until BlockFieldEnvironment.DIFFICULTY_LEVELS).toSet(), difficulties)
        assertTrue(minimumDensity < 0.65f)
        assertTrue(maximumDensity > 0.85f)
    }

    @Test
    fun `every level demands at least one jump`() {
        // A gap-free base level trains a controller that never leaves the ground,
        // which is the one skill the planner actually needs from this policy.
        val environment = BlockFieldEnvironment()
        val seen = mutableMapOf<Int, Int>()
        repeat(512) { seed ->
            val transition = environment.reset(seed.toLong())
            seen.merge(transition.scenario, 1, Int::plus)
        }
        assertEquals(BlockFieldEnvironment.DIFFICULTY_LEVELS, seen.size)
        repeat(BlockFieldEnvironment.DIFFICULTY_LEVELS) { level ->
            assertTrue(
                BlockFieldEnvironment.gapCountForLevel(level) >= 1,
                "level $level generates no gap",
            )
        }
    }

    @Test
    fun `curriculum ceiling is emphasised but evaluation stays uniform`() {
        val environment = BlockFieldEnvironment()
        val ceiling = 3
        var atCeiling = 0
        repeat(400) { seed ->
            if (environment.reset(seed.toLong(), maxDifficulty = ceiling).scenario == ceiling) {
                atCeiling++
            }
        }
        // Uniform sampling over 0..3 would land near 25%; the ceiling is weighted.
        assertTrue(atCeiling > 200, "expected the ceiling to be emphasised, got $atCeiling/400")

        var uniformAtTop = 0
        repeat(400) { seed ->
            val transition = environment.reset(
                seed.toLong(),
                maxDifficulty = BlockFieldEnvironment.UNIFORM_DIFFICULTY,
            )
            if (transition.scenario == BlockFieldEnvironment.DIFFICULTY_LEVELS - 1) {
                uniformAtTop++
            }
        }
        assertTrue(
            uniformAtTop in 30..120,
            "evaluation sampling must stay uniform, got $uniformAtTop/400",
        )
    }

    @Test
    fun `refusing to move ends the episode as a stall rather than idling out the clock`() {
        val environment = BlockFieldEnvironment()
        environment.reset(77L)
        val idle = intArrayOf(1, 1, 0, 0, 3)

        var transition = environment.step(idle)
        while (!transition.terminated && !transition.truncated) {
            assertTrue(transition.observation.all(Float::isFinite))
            transition = environment.step(idle)
        }

        assertEquals(RlOutcome.STALLED, transition.outcome)
        assertTrue(transition.terminated, "a stall is a failure, not a truncation")
        assertTrue(
            transition.ticks < BlockFieldEnvironment.MAX_TICKS / 2,
            "a parked body must not consume the full tick budget, got ${transition.ticks}",
        )
        assertTrue(transition.reward < 0.0f, "stalling must cost as much as failing")
    }
}
