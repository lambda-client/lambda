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

import com.lambda.interaction.handlers.parseFriendNames
import com.lambda.interaction.handlers.writeFriendNames
import tools.jackson.databind.json.JsonMapper
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class FriendNameCacheTest {

    private val mapper = JsonMapper.builder().build()

    @Test
    fun `round trips names and drops malformed entries`() {
        val first = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5")
        val second = UUID.fromString("7bbc65a9-0670-41cb-a331-f2989479362c")
        val original = mapOf(first to "jeb_", second to "AutoCrysta1")

        val json = writeFriendNames(original, mapper)
        assertEquals(original, parseFriendNames(json, mapper))

        val polluted = """
            {
              "${first}": "jeb_",
              "not-a-uuid": "nobody",
              "${second}": 42,
              "null": null
            }
        """.trimIndent()
        assertEquals(mapOf(first to "jeb_"), parseFriendNames(polluted, mapper))

        assertEquals(emptyMap(), parseFriendNames("[]", mapper))
        assertEquals(emptyMap(), parseFriendNames("not json", mapper))
    }
}