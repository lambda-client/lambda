package com.lambda.config.serializer

import com.google.gson.*
import com.lambda.module.tag.ModuleTag
import java.lang.reflect.Type

object ModuleTagSerializer : JsonSerializer<ModuleTag>, JsonDeserializer<ModuleTag> {
    override fun serialize(
        src: ModuleTag?,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement =
        src?.let {
            JsonPrimitive(it.name)
        } ?: JsonNull.INSTANCE

    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): ModuleTag =
        json?.asString?.let { ModuleTag(it) } ?: throw JsonParseException("Invalid module tag format")
}