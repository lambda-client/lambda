/*
 * Copyright 2025 Lambda
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
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.lambda.config.Codec
import com.lambda.config.Stringifiable
import com.mojang.authlib.GameProfile
import java.lang.reflect.Type
import java.util.*

// Yeah yeah I know, there's already a serializer for GameProfile in the Minecraft codebase.
// But who cares, I'm doing it again.
// What you gon' do bout it, huh?
// That's what I thought.
object GameProfileCodec : Codec<GameProfile>, Stringifiable<GameProfile> {
    override fun serialize(
        src: GameProfile,
        typeOfSrc: Type?,
        context: JsonSerializationContext?,
    ): JsonElement =
        JsonObject().apply {
            addProperty("name", src.name)
            addProperty("id", src.id.toString())
        }

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type?,
        context: JsonDeserializationContext?,
    ): GameProfile {
        val name = json.asJsonObject.get("name")?.asString ?: "nil"
        val id = json.asJsonObject.get("id")?.asString ?: "00000000-0000-0000-0000-000000000000"
        val parsedId =
            if (id.length == 32) id.replaceFirst(
                "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})".toRegex(),
                "$1-$2-$3-$4-$5"
            )
            else id

        return GameProfile(UUID.fromString(parsedId), name)
    }

    override fun stringify(value: GameProfile) = value.toString()
}
