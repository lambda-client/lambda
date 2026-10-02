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
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BulkProfileTest {

    @Test
    fun `converts undashed mojang id`() {
        val raw = "7bbc65a9067041cba331f2989479362c"
        val profile = toGameProfile(raw, "AutoCrysta1")
        assertEquals(raw, profile?.id.toString().filterNot { it == '-' })
        assertEquals("AutoCrysta1", profile?.name)
    }

    @Test
    fun `converts uppercase undashed id`() {
        val raw = "7BBC65A9067041CBA331F2989479362C"
        val profile = toGameProfile(raw, "AutoCrysta1")
        assertEquals(raw.lowercase(), profile?.id.toString().filterNot { it == '-' })
    }

    @Test
    fun `rejects dashed id`() {
        assertNull(toGameProfile("069a79f4-44e9-4726-a5be-fca90e38aaf5", "jeb_"))
    }

    @Test
    fun `rejects non hex id`() {
        assertNull(toGameProfile("zzzzzzzz064444zzzz4444zzzz4444zzzz", "nope"))
    }

    @Test
    fun `rejects wrong length id`() {
        assertNull(toGameProfile("069a79f444e94726a5befca90e38aaf", "nope"))
        assertNull(toGameProfile("", "empty"))
    }
}