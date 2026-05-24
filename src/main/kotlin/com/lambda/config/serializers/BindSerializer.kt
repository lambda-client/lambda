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

import com.lambda.config.Serializer
import com.lambda.config.settings.complex.Bind
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.deser.std.StdDeserializer
import tools.jackson.databind.node.ObjectNode
import tools.jackson.databind.ser.std.StdSerializer

@Suppress("unused")
object BindSerializer : Serializer<Bind>() {
	override val type = Bind::class.java

	override val serializer = object : StdSerializer<Bind>(type) {
		override fun serialize(value: Bind, gen: JsonGenerator, ctxt: SerializationContext) {
			gen.writeStartObject()
				.writeNumberProperty("key", value.key)
				.writeNumberProperty("modifiers", value.modifiers)
				.writeNumberProperty("mouse", value.mouse)
				.writeEndObject()
		}
	}

	override val deSerializer = object : StdDeserializer<Bind>(type) {
		override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Bind {
			val node = p.readValueAsTree<ObjectNode>()
			return Bind(node.get("key").asInt(), node.get("modifiers").asInt(), node.get("mouse").asInt())
		}
	}
}
