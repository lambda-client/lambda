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
import com.google.gson.JsonNull
import com.google.gson.JsonParseException
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.lambda.config.Codec
import com.lambda.util.KeyCode
import net.minecraft.world.attribute.EnvironmentAttributeModifier.override
import java.lang.reflect.Type

@Suppress("unused")
object KeyCodeCodec : Codec<KeyCode> {
    override val type = KeyCode::class.java

    override fun serialize(
        src: KeyCode?,
        typeOfSrc: Type?,
        context: JsonSerializationContext?,
    ): JsonElement =
        src?.let {
            JsonPrimitive(it.name)
        } ?: JsonNull.INSTANCE

    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?,
    ): KeyCode =
        json?.asString?.let(KeyCode::fromKeyName) ?: throw JsonParseException("Invalid key code format")
}
