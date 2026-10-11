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

@file:Suppress("unused")

package com.lambda.config.serializers

import com.lambda.config.Deserializer
import com.lambda.config.Serializer
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.JsonNode
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.json.JsonMapper

class UnsupportedFriendFileException(message: String) : Exception(message)

/**
 * Usernames from an imported friend list.
 */
data class FriendList(val names: List<String>) {
	companion object {
		private val NAME = Regex("^[A-Za-z0-9_]{1,16}$")

		/**
		 * Reads a friend list from either JSON (arrays and `name`/`role` objects) or plain text, one name per line.
		 */
		fun parse(text: String, mapper: JsonMapper): FriendList {
			val body = text.removePrefix("﻿").trim()
			val list = if (body.startsWith("{") || body.startsWith("[")) {
				runCatching { mapper.readValue(body, FriendList::class.java) }.getOrNull()
					?: throw UnsupportedFriendFileException("The file is not valid JSON.")
			} else {
				FriendList(clean(body.lines().map { it.substringBefore('#').trim() }))
			}
			if (list.names.isEmpty()) throw UnsupportedFriendFileException("No friends found in this file.")
			return list
		}

		internal fun clean(names: List<String>) = names
			.filter { NAME.matches(it) }
			.distinctBy { it.lowercase() }
	}
}

object FriendListSerializer : Serializer<FriendList>(FriendList::class.java) {
	override fun serialize(friendList: FriendList, gen: JsonGenerator, ctxt: SerializationContext) {
		gen.writeStartArray()
		friendList.names.forEach { name ->
			gen.writeString(name)
		}
		gen.writeEndArray()
	}
}

object FriendListDeserializer : Deserializer<FriendList>(FriendList::class.java) {
	override fun deserialize(p: JsonParser, ctxt: DeserializationContext) =
		FriendList(FriendList.clean(namesIn(ctxt.readTree(p))))

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
