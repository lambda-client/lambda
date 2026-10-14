/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pathing

import com.lambda.pathing.core.Stance
import com.lambda.pathing.session.InterestPrimer
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.streaming.PathingWorld
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The first coarse route of a journey reads only what the journey primed before the body walks. A parkour
 * checkpoint 60 blocks away was reached by dropping through a hole and climbing a line 14 blocks off the
 * straight line to the goal; primed as a thin corridor, that line was unknown, the first route went back
 * the way the body came and the body walked it before the next settle knew better. A near goal therefore
 * primes the whole box between start and goal; a far one keeps the sampled corridor.
 */
class InterestPrimerTest {
	private val source = FakeCaptureSource(groundY = 64, viewDistance = 32).apply { loadSquare(30) }
	private val world = PathingWorld(SimulationSnapshotBounds(Int.MIN_VALUE, -64, Int.MIN_VALUE, Int.MAX_VALUE, 319, Int.MAX_VALUE), source)

	private fun capture() {
		while (world.pendingInterest > 0) world.advance(1_000.0)
	}

	@Test
	fun `a near journey primes the whole box between its start and goal`() {
		InterestPrimer.primeJourney(world, Stance(-67, 121, 8), Stance(-114, 121, 42))
		capture()
		assertTrue(world.snapshot.isKnown(-98, 116, 14), "a line 14 blocks off the straight line to the goal")
		assertTrue(world.snapshot.isKnown(-64, 106, 8), "the bottom of a drop 15 blocks below the start")
		assertTrue(world.snapshot.isKnown(-125, 121, 0), "the corner of the box beside the goal")
	}

	@Test
	fun `a far journey primes a sampled corridor rather than the whole box`() {
		InterestPrimer.primeJourney(world, Stance(0, 64, 0), Stance(400, 64, 0))
		capture()
		assertTrue(world.snapshot.isKnown(200, 64, 0), "the straight line is sampled")
		assertFalse(world.snapshot.isKnown(200, 64, 24), "terrain beside the corridor waits for the coarse route to ask for it")
	}
}
