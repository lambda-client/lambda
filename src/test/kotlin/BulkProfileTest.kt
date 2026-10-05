/*
 * Copyright 2026 Lambda
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

import com.lambda.network.mojang.toGameProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BulkProfileTest {

    @Test
    fun `converts undashed mojang id`() {
        val raw = "0123456789abcdef0123456789abcdef"
        val profile = toGameProfile(raw, "Steve")
        assertEquals(raw, profile?.id.toString().filterNot { it == '-' })
        assertEquals("Steve", profile?.name)
    }

    @Test
    fun `converts uppercase undashed id`() {
        val raw = "0123456789ABCDEF0123456789ABCDEF"
        val profile = toGameProfile(raw, "Steve")
        assertEquals(raw.lowercase(), profile?.id.toString().filterNot { it == '-' })
    }

    @Test
    fun `rejects dashed id`() {
        assertNull(toGameProfile("853c80ef-3c37-49fd-aa49-938b674adae6", "jeb_"))
    }

    @Test
    fun `rejects non hex id`() {
        assertNull(toGameProfile("zzzzzzzz064444zzzz4444zzzz4444zzzz", "nope"))
    }

    @Test
    fun `rejects wrong length id`() {
        assertNull(toGameProfile("853c80ef3c3749fdaa49938b674adae", "nope"))
        assertNull(toGameProfile("", "empty"))
    }
}