package com.lambda.config.serializer

import com.google.gson.*
import net.minecraft.block.Block
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier
import java.lang.reflect.Type

object BlockSerializer : JsonSerializer<Block>, JsonDeserializer<Block> {
    override fun serialize(
        src: Block?,
        typeOfSrc: Type?,
        context: JsonSerializationContext?,
    ): JsonElement =
        src?.let {
            JsonPrimitive(Registries.BLOCK.getId(it).toString())
        } ?: JsonNull.INSTANCE

    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?,
    ): Block =
        Registries.BLOCK.getOrEmpty(Identifier.ofVanilla(json?.asString)).orElseThrow()
}
