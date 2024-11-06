/*
 * Copyright 2024 Lambda
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

import com.google.gson.*
import java.lang.reflect.Type
import java.util.*

object OptionalSerializer : JsonSerializer<Optional<Any>>, JsonDeserializer<Optional<Any>> {
    override fun serialize(src: Optional<Any>?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement =
        src?.map { context?.serialize(it) }?.orElse(JsonNull.INSTANCE) ?: JsonNull.INSTANCE

    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): Optional<Any> =
        Optional.ofNullable(json?.let { context?.deserialize(it, typeOfT) ?: Optional.empty<Any>() })
}
