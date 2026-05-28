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

import com.fasterxml.jackson.core.JsonParseException
import com.lambda.config.Stringifiable
import com.lambda.config.TypeAdapter
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.JsonNode
import tools.jackson.databind.SerializationContext
import java.util.*

@Suppress("unused")
object UuidTypeAdapter : TypeAdapter<UUID>(), Stringifiable<UUID> {
    override val type = UUID::class.java

    override val serializer = object : Serializer<UUID>(type) {
        override fun serialize(uuid: UUID, gen: JsonGenerator, ctxt: SerializationContext) {
            gen.writeString(uuid.toString())
        }
    }

    override val deserializer = object : Deserializer<UUID>(type) {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): UUID {
            val jsonNode = p.readValueAsTree<JsonNode>()

            val rawId = when {
                jsonNode.isString -> jsonNode.stringValue()
                jsonNode.isObject && jsonNode.has("id") -> jsonNode.get("id").stringValue()
                else -> throw JsonParseException("Cannot deserialize UUID from: $jsonNode")
            }

            val parsedId = if (rawId.length == 32)
                rawId.replace(Regex("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})"), "$1-$2-$3-$4-$5")
            else rawId

            return UUID.fromString(parsedId)
        }
    }

    override fun stringify(value: UUID) = value.toString()
}
