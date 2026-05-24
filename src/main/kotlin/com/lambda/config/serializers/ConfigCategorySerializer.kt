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

import com.lambda.config.ConfigCategory
import com.lambda.config.Serializer
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.deser.std.StdDeserializer
import tools.jackson.databind.node.ObjectNode
import tools.jackson.databind.ser.std.StdSerializer

object ConfigCategorySerializer : Serializer<ConfigCategory>() {
	override val type = ConfigCategory::class.java

	override val serializer = object : StdSerializer<ConfigCategory>(type) {
		override fun serialize(value: ConfigCategory, gen: JsonGenerator, ctxt: SerializationContext) {
			gen.writeStartObject()
			value.configs.forEach { config ->
				val serialized = mapper.valueToTree<ObjectNode>(config)
				if (serialized.isEmpty) return@forEach
				gen.writeName(config.name)
				gen.writeTree(serialized)
			}
			gen.writeEndObject()
		}
	}

	override val deSerializer = object : StdDeserializer<ConfigCategory>(type) {
		override fun deserialize(p: JsonParser, ctxt: DeserializationContext): ConfigCategory {
			throw IllegalStateException("Attempted to initialize a ConfigCategory directly from JSON! All ConfigCategory's should be updated after standard initialization.")
		}

		override fun deserialize(jp: JsonParser, ctxt: DeserializationContext, newValue: ConfigCategory): ConfigCategory {
			return super.deserialize(jp, ctxt, newValue)
		}
	}
}