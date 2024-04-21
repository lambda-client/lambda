package com.lambda.config.serializer

import com.google.gson.*
import com.mojang.authlib.GameProfile
import java.lang.reflect.Type
import java.util.*

// Yeah yeah I know, there's already a serializer for GameProfile in the Minecraft codebase.
// But who cares, I'm doing it again.
// What you gon' do bout it, huh?
// That's what I thought.
object GameProfileSerializer : JsonSerializer<GameProfile>, JsonDeserializer<GameProfile> {
    override fun serialize(
        src: GameProfile?,
        typeOfSrc: Type?,
        context: JsonSerializationContext?,
    ): JsonElement =
        src?.let {
            JsonObject().apply {
                addProperty("name", it.name)
                addProperty("uuid", it.id.toString())
            }
        } ?: JsonNull.INSTANCE

    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?,
    ): GameProfile =
        GameProfile(
            UUID.fromString(json?.asJsonObject?.get("uuid")?.asString),
            json?.asJsonObject?.get("name")?.asString
        )
}
