package com.lambda.config.serializer

import com.google.gson.*
import java.awt.Color
import java.lang.reflect.Type

object ColorSerializer : JsonSerializer<Color>, JsonDeserializer<Color> {
    override fun serialize(
        src: Color?,
        typeOfSrc: Type?,
        context: JsonSerializationContext?,
    ): JsonElement =
        src?.let {
            JsonPrimitive("${it.red},${it.green},${it.blue},${it.alpha}")
        } ?: JsonNull.INSTANCE

    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?,
    ): Color =
        json?.asString?.split(",")?.let {
            when (it.size) {
                3 -> Color(it[0].toInt(), it[1].toInt(), it[2].toInt())
                4 -> Color(it[0].toInt(), it[1].toInt(), it[2].toInt(), it[3].toInt())
                else -> throw JsonParseException("Invalid color format")
            }
        } ?: throw JsonParseException("Invalid color format")
}