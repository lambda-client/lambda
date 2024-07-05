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
