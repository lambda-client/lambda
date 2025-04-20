/*
 * Copyright 2025 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package pathing

import com.lambda.pathing.dstar.Key
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/*
 * Copyright 2025 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

internal class KeyTest {

    @Test
    fun `compareTo checks first element primarily`() {
        assertTrue(Key(1.0, 10.0) < Key(2.0, 1.0))
        assertTrue(Key(3.0, 1.0) > Key(2.0, 10.0))
    }

    @Test
    fun `compareTo checks second element when first elements are equal`() {
        assertTrue(Key(5.0, 1.0) < Key(5.0, 2.0))
        assertTrue(Key(5.0, 3.0) > Key(5.0, 2.0))
    }

    @Test
    fun `compareTo handles equal keys`() {
        assertEquals(0, Key(5.0, 2.0).compareTo(Key(5.0, 2.0)))
        assertTrue(Key(5.0, 2.0) <= Key(5.0, 2.0))
        assertTrue(Key(5.0, 2.0) >= Key(5.0, 2.0))
    }

    @Test
    fun `compareTo handles infinity`() {
        assertTrue(Key(1000.0, 1000.0) < Key.INFINITY)
        assertTrue(Key.INFINITY > Key(0.0, 0.0))
        assertEquals(0, Key.INFINITY.compareTo(Key.INFINITY))
    }

    @Test
    fun `equals checks both elements`() {
        assertEquals(Key(1.0, 2.0), Key(1.0, 2.0))
        assertNotEquals(Key(1.0, 2.0), Key(2.0, 2.0))
        assertNotEquals(Key(1.0, 2.0), Key(1.0, 3.0))
    }

    @Test
    fun `hashCode is consistent with equals`() {
        assertEquals(Key(1.0, 2.0).hashCode(), Key(1.0, 2.0).hashCode())
        assertNotEquals(Key(1.0, 2.0).hashCode(), Key(1.0, 3.0).hashCode())
    }
}
