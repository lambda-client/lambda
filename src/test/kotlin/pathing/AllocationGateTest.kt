/*
 * Copyright 2026 Lambda
 */
package pathing

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Hard upper bound on heap allocated per simulated tick.
 *
 * The bound is set from the 7 Sep 2026 baseline with headroom for JIT and GC noise; each
 * physics or world-layer stage that reduces per-tick allocation tightens it. The rollout
 * fixture is the same one the benchmark and the parallel-scaling probe use, so the three
 * numbers stay comparable.
 */
class AllocationGateTest {
    @Test
    fun `allocation per simulated tick stays under the bound`() {
        val fixture = RolloutFixture()
        repeat(2000) { fixture.rolloutOnce(it) }

        val rollouts = 4000
        val ticks = rollouts.toLong() * RolloutFixture.FRAMES
        val samples = (0 until 3).map {
            PlannerBenchmark.allocatedBytes { repeat(rollouts) { i -> fixture.rolloutOnce(i) } } / ticks
        }
        val best = samples.min()
        println("[alloc] bytes/tick samples=$samples best=$best bound=$MAX_BYTES_PER_TICK")
        assertTrue(best <= MAX_BYTES_PER_TICK, "allocation per tick $best exceeds bound $MAX_BYTES_PER_TICK")
    }

    companion object {
        const val MAX_BYTES_PER_TICK = 10_000L // 7 Sep 2026: baseline 7,629; after Stage 3 6,600–7,300 (JIT-dependent)
    }
}
