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

import com.lambda.config.serializers.FriendList
import com.lambda.config.serializers.FriendListDeserializer
import com.lambda.config.serializers.FriendListSerializer
import com.lambda.config.serializers.UnsupportedFriendFileException
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.module.SimpleModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FriendFileParserTest {

    private val mapper = JsonMapper.builder()
        .addModule(
            SimpleModule()
                .addSerializer(FriendList::class.java, FriendListSerializer)
                .addDeserializer(FriendList::class.java, FriendListDeserializer)
        )
        .build()

    private fun parse(text: String) = FriendList.parse(text, mapper).names

    @Test
    fun `round trips through the serializer`() {
        val original = FriendList(listOf("jeb_", "Dinner"))
        val json = mapper.writeValueAsString(original)
        assertEquals("""["jeb_","Dinner"]""", json)
        assertEquals(original, mapper.readValue(json, FriendList::class.java))
    }

    @Test
    fun `parses mio socials object with friends and enemies`() {
        val json = """
            {
              "socials": [
                {"name": "jeb_", "role": "friend"},
                {"name": "Dinner", "role": "friend"},
                {"name": "Grumm", "role": "enemy"}
              ]
            }
        """.trimIndent()

        assertEquals(listOf("jeb_", "Dinner"), parse(json))
    }

    @Test
    fun `parses array of objects`() {
        val json = """[{"name":"jeb_"},{"name":"Dinner"}]"""
        assertEquals(listOf("jeb_", "Dinner"), parse(json))
    }

    @Test
    fun `parses array of strings`() {
        assertEquals(listOf("jeb_", "Dinner"), parse("""["jeb_","Dinner"]"""))
    }

    @Test
    fun `parses single object with name`() {
        assertEquals(listOf("jeb_"), parse("""{"name":"jeb_"}"""))
    }

    @Test
    fun `parses plain text ignoring blanks and comments`() {
        val text = """
            # my friends
            jeb_

              notch  # trailing comment
            Dinner
        """.trimIndent()

        assertEquals(listOf("jeb_", "notch", "Dinner"), parse(text))
    }

    @Test
    fun `deduplicates names case insensitively keeping first`() {
        assertEquals(listOf("Jeb_"), parse("Jeb_\njeb_\nJEB_"))
    }

    @Test
    fun `skips invalid usernames`() {
        assertEquals(listOf("jeb_"), parse("""[{"name":"jeb_"},{"name":"has space"},{"name":"way_too_long_name"}]"""))
        assertEquals(listOf("jeb_"), parse("jeb_\nnot a valid name"))
    }

    @Test
    fun `ignores unrelated nested names`() {
        assertEquals(listOf("jeb_"), parse("""{"profile":{"name":"x"},"friends":["jeb_"]}"""))
    }

    @Test
    fun `handles utf8 byte order mark`() {
        assertEquals(listOf("jeb_"), parse("\uFEFFjeb_"))
    }

    @Test
    fun `rejects garbage json`() {
        assertFailsWith<UnsupportedFriendFileException> { parse("{ this is not json ") }
    }

    @Test
    fun `rejects empty file`() {
        assertFailsWith<UnsupportedFriendFileException> { parse("   \n  ") }
    }

    @Test
    fun `rejects numbers as entries`() {
        assertFailsWith<UnsupportedFriendFileException> { parse("[1, 2, 3]") }
    }

    @Test
    fun `rejects unsupported json structure`() {
        assertFailsWith<UnsupportedFriendFileException> { parse("""{"unrelated":{"nested":42}}""") }
    }

    @Test
    fun `excludes every enemy from an all enemy file`() {
        val json = """[{"name":"a","role":"enemy"},{"name":"b","role":"enemy"}]"""
        val error = assertFailsWith<UnsupportedFriendFileException> { parse(json) }
        assertEquals("No friends found in this file.", error.message)
    }
}