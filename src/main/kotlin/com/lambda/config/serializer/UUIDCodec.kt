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

package com.lambda.config.serializer

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.google.gson.JsonSerializationContext
import com.lambda.config.Codec
import com.lambda.config.Stringifiable
import java.lang.reflect.Type
import java.util.*

object UUIDCodec : Codec<UUID>, Stringifiable<UUID> {
    override fun serialize(
        src: UUID,
        typeOfSrc: Type?,
        context: JsonSerializationContext?,
    ): JsonElement = context?.serialize(src.toString()) ?: throw JsonParseException("No serialization context")

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type?,
        context: JsonDeserializationContext?,
    ): UUID {
        val rawId = when {
            json.isJsonPrimitive -> json.asString
            json.isJsonObject && json.asJsonObject.has("id") -> json.asJsonObject.get("id").asString
            else -> throw JsonParseException("Cannot deserialize UUID from: $json")
        }

        val parsedId =
            if (rawId.length == 32) rawId.replaceFirst(
                "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})".toRegex(),
                "$1-$2-$3-$4-$5"
            )
            else rawId

        return UUID.fromString(parsedId)
    }

    override fun stringify(value: UUID) = value.toString()
}
