/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pathing

import com.lambda.pathing.graph.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class KeyTest {
    @Test
    fun `keys are compared lexicographically`() {
        assertTrue(Key(1.0, 10.0) < Key(2.0, 1.0))
        assertTrue(Key(5.0, 1.0) < Key(5.0, 2.0))
        assertTrue(Key(1000.0, 1000.0) < Key.INFINITY)
    }

    @Test
    fun `key equality uses both components`() {
        assertEquals(Key(1.0, 2.0), Key(1.0, 2.0))
        assertNotEquals(Key(1.0, 2.0), Key(1.0, 3.0))
        assertNotEquals(Key(1.0, 2.0), Key(2.0, 2.0))
    }
}
