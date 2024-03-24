package com.lambda.config.serializer

import com.google.gson.*
import com.lambda.util.KeyCode
import java.lang.reflect.Type

// ToDo: Use key lookup table to store actual key names
object KeyCodeSerializer : JsonSerializer<KeyCode>, JsonDeserializer<KeyCode> {
    override fun serialize(
        src: KeyCode?,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement =
        src?.let {
            JsonPrimitive(it.key)
        } ?: JsonNull.INSTANCE

    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): KeyCode =
        json?.asInt?.let { KeyCode(it) } ?: throw JsonParseException("Invalid key code format")
}