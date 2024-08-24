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
                addProperty("id", it.id.toString())
            }
        } ?: JsonNull.INSTANCE

    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?,
    ): GameProfile {
        val id = json?.asJsonObject?.get("id")?.asString
        val parsedId =
            if (id?.length == 32) id.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})".toRegex(), "$1-$2-$3-$4-$5")
            else id

        return GameProfile(
            UUID.fromString(parsedId),
            json?.asJsonObject?.get("name")?.asString
        )
    }
}
