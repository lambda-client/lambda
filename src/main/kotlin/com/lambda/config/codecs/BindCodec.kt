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

package com.lambda.config.codecs

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.lambda.config.Codec
import com.lambda.config.settings.complex.Bind
import java.lang.reflect.Type

@Suppress("unused")
object BindCodec : Codec<Bind> {
	override val type = Bind::class.java

	override fun serialize(src: Bind, typeOfSrc: Type, context: JsonSerializationContext): JsonElement =
		JsonObject().apply {
			addProperty("key", src.key)
			addProperty("modifiers", src.modifiers)
			addProperty("mouse", src.mouse)
		}

	override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext) =
		json.asJsonObject.let { obj ->
			Bind(obj.get("key").asInt, obj.get("modifiers").asInt, obj.get("mouse").asInt)
		}
}