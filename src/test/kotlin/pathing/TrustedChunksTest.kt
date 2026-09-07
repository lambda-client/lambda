/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pathing

import com.lambda.pathing.world.TrustedChunks
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrustedChunksTest {
    @Test
    fun `a chunk is trusted when loaded and inside view distance after the edge margin`() {
        val source = FakeCaptureSource(viewDistance = 4).apply { loadSquare(10) }
        val trusted = TrustedChunks(source)
        assertTrue(trusted.isTrusted(0, 0))
        assertTrue(trusted.isTrusted(5, 0), "distance 5 minus margin 2 is 3, 9 < 16")
        assertFalse(trusted.isTrusted(6, 0), "distance 6 minus margin 2 is 4, 16 is not < 16")
        assertTrue(trusted.isTrusted(4, 4), "shaved to (2, 2): 8 < 16")
        assertFalse(trusted.isTrusted(5, 5), "shaved to (3, 3): 18 is not < 16")

        source.unload(1, 0)
        assertFalse(trusted.isTrusted(1, 0), "unloaded chunks are never trusted")

        source.playerChunkX = 10
        assertFalse(trusted.isTrusted(0, 0), "the rule follows the player")
        assertTrue(trusted.isTrusted(10, 0))
    }

    @Test
    fun `the published set refreshes every fourth tick`() {
        val source = FakeCaptureSource(viewDistance = 4).apply { loadSquare(3) }
        val trusted = TrustedChunks(source)
        assertFalse(trusted.contains(0, 0), "nothing published before the first tick")

        trusted.tick()
        assertTrue(trusted.contains(0, 0))
        assertFalse(trusted.contains(4, 0), "not loaded")

        source.load(4, 0)
        repeat(TrustedChunks.TRUSTED_REFRESH_TICKS - 1) { trusted.tick() }
        assertFalse(trusted.contains(4, 0), "stale until the refresh tick")
        trusted.tick()
        assertTrue(trusted.contains(4, 0))
    }
}
