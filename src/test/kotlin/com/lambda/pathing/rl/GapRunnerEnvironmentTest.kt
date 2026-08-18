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

class GapRunnerEnvironmentTest {
    @Test
    fun `reset is deterministic and has the advertised observation shape`() {
        val first = GapRunnerEnvironment().reset(0x5EED)
        val second = GapRunnerEnvironment().reset(0x5EED)

        assertEquals(GapRunnerEnvironment.OBSERVATION_SIZE, first.observation.size)
        assertContentEquals(first.observation, second.observation)
        assertEquals(first.scenario, second.scenario)
        assertEquals(RlOutcome.RUNNING, first.outcome)
        assertFalse(first.terminated)
        assertFalse(first.truncated)
    }

    @Test
    fun `an idle policy reaches the time limit without protocol-invalid values`() {
        val environment = GapRunnerEnvironment()
        environment.reset(12L)
        val idle = intArrayOf(1, 1, 0, 0, 3)

        var transition = environment.step(idle)
        while (!transition.terminated && !transition.truncated) {
            assertTrue(transition.observation.all(Float::isFinite))
            transition = environment.step(idle)
        }

        assertEquals(RlOutcome.TIME_LIMIT, transition.outcome)
        assertTrue(transition.truncated)
        assertEquals(GapRunnerEnvironment.MAX_TICKS, transition.ticks)
    }
}
