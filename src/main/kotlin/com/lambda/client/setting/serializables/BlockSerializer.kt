package com.lambda.client.setting.serializables

import com.google.gson.*
import java.lang.reflect.Type
import net.minecraft.block.Block

object BlockSerializer : JsonSerializer<Block>, JsonDeserializer<Block> {
    override fun serialize(src: Block, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        return JsonPrimitive(src.registryName.toString())
    }

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Block? {
        val stringValue = json.asJsonPrimitive.asString
        return Block.getBlockFromName(stringValue)
    }
}