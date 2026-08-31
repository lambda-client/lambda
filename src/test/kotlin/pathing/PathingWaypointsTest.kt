/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pathing

import com.lambda.command.commands.PathingWaypoints
import com.lambda.pathing.core.Stance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PathingWaypointsTest {
    private val origin = Stance(10, 64, -20)

    @Test
    fun `absolute triples parse in order`() {
        assertEquals(
            listOf(Stance(1, 2, 3), Stance(-4, 5, -6)),
            PathingWaypoints.parse("1 2 3 -4 5 -6", origin),
        )
    }

    @Test
    fun `tilde coordinates resolve against the origin`() {
        assertEquals(
            listOf(Stance(10, 64, -20), Stance(15, 63, -20)),
            PathingWaypoints.parse("~ ~ ~ ~5 ~-1 ~0", origin),
        )
    }

    @Test
    fun `ragged or malformed input is refused`() {
        assertNull(PathingWaypoints.parse("1 2", origin))
        assertNull(PathingWaypoints.parse("1 2 3 4", origin))
        assertNull(PathingWaypoints.parse("a b c", origin))
        assertNull(PathingWaypoints.parse("", origin))
        assertNull(PathingWaypoints.parse("1 2 3.5", origin))
    }

    @Test
    fun `extra whitespace is tolerated`() {
        assertEquals(
            listOf(Stance(1, 2, 3)),
            PathingWaypoints.parse("  1   2  3 ", origin),
        )
    }
}
