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

package com.lambda.config.serializers

import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

class UnsupportedFriendFileException(message: String) : Exception(message)

/**
 * Extracts usernames from an imported friend list, supporting both JSON
 * (arrays and `name`/`role` objects) and plain text files.
 */
object FriendListDeserializer {
    private val NAME = Regex("^[A-Za-z0-9_]{1,16}$")

    fun parse(text: String, mapper: JsonMapper): List<String> {
        val body = text.removePrefix("\uFEFF").trim()
        val raw = if (body.startsWith("{") || body.startsWith("[")) {
            namesIn(
                runCatching { mapper.readTree(body) }.getOrNull()
                    ?: throw UnsupportedFriendFileException("The file is not valid JSON.")
            )
        } else {
            body.lines().map { it.substringBefore('#').trim() }
        }
        return raw.filter { NAME.matches(it) }
            .distinctBy { it.lowercase() }
            .ifEmpty { throw UnsupportedFriendFileException("No friends found in this file.") }
    }

    private fun namesIn(node: JsonNode): List<String> = when {
        node.isString -> listOf(node.stringValue())
        node.isArray -> node.flatMap { namesIn(it) }
        node.isObject && node.has("name") -> {
            val name = node.get("name").takeIf { it.isString }?.stringValue()
            val role = node.get("role")?.takeIf { it.isString }?.stringValue()
            if (name != null && (role == null || role.equals("friend", true))) {
                listOf(name)
            } else {
                emptyList()
            }
        }
        node.isObject -> node.properties().filter { it.value.isArray }.flatMap { namesIn(it.value) }
        else -> emptyList()
    }
}