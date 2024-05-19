package com.lambda.config.serializer

import com.google.gson.*
import com.lambda.util.KeyCode
import com.lambda.util.primitives.extension.displayValue
import java.lang.reflect.Type

object KeyCodeSerializer : JsonSerializer<KeyCode>, JsonDeserializer<KeyCode> {
    override fun serialize(
        src: KeyCode?,
        typeOfSrc: Type?,
        context: JsonSerializationContext?,
    ): JsonElement =
        src?.let {
            JsonPrimitive(it.displayValue)
        } ?: JsonNull.INSTANCE

    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?,
    ): KeyCode =
        json?.asString?.let(KeyCode::byNameOrNull) ?: throw JsonParseException("Invalid key code format")
}