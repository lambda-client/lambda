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

import com.lambda.config.serializers.FriendNameDeserializer
import com.lambda.config.serializers.FriendNameSerializer
import com.lambda.config.serializers.FriendNames
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.module.SimpleModule
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class FriendNameCacheTest {

    private val mapper = JsonMapper.builder()
        .addModule(
            SimpleModule()
                .addSerializer(FriendNames::class.java, FriendNameSerializer)
                .addDeserializer(FriendNames::class.java, FriendNameDeserializer)
        )
        .build()

    private fun read(json: String) = mapper.readValue(json, FriendNames::class.java).names

    @Test
    fun `round trips names and drops malformed entries`() {
        val first = UUID.fromString("853c80ef-3c37-49fd-aa49-938b674adae6")
        val second = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val original = mapOf(first to "jeb_", second to "Steve")

        val json = mapper.writeValueAsString(FriendNames(original))
        assertEquals(original, read(json))

        val polluted = """
            {
              "${first}": "jeb_",
              "not-a-uuid": "nobody",
              "${second}": 42,
              "null": null
            }
        """.trimIndent()
        assertEquals(mapOf(first to "jeb_"), read(polluted))

        assertEquals(emptyMap(), read("[]"))
        assertFails { read("not json") }
    }
}
