/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pathing

import com.lambda.pathing.manager.refinedRouteAnchorIndex
import com.lambda.util.world.fastVectorOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PathfinderRouteAnchorTest {
    @Test
    fun `mid shortcut anchors to its head but final endpoint is consumed`() {
        val route = listOf(fastVectorOf(0, 64, 0), fastVectorOf(6, 64, 0))

        assertEquals(0, refinedRouteAnchorIndex(route, fastVectorOf(3, 64, 0)))
        assertEquals(1, refinedRouteAnchorIndex(route, fastVectorOf(6, 64, 0)))
    }

    @Test
    fun `shared refined node advances to the following segment`() {
        val route = listOf(
            fastVectorOf(0, 64, 0),
            fastVectorOf(6, 64, 0),
            fastVectorOf(6, 64, 6),
        )

        assertEquals(1, refinedRouteAnchorIndex(route, fastVectorOf(6, 64, 0)))
        assertEquals(2, refinedRouteAnchorIndex(route, fastVectorOf(6, 64, 6)))
    }

    @Test
    fun `off corridor stance is not projected onto route`() {
        val route = listOf(fastVectorOf(0, 64, 0), fastVectorOf(6, 64, 0))

        assertNull(refinedRouteAnchorIndex(route, fastVectorOf(3, 64, 3)))
    }
}
