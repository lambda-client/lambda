package com.lambda.client.setting.serializables

import com.google.gson.JsonDeserializer
import com.google.gson.JsonSerializer
import com.google.gson.*
import java.lang.reflect.Type
import net.minecraft.util.math.BlockPos

object BlockPosSerializer : JsonSerializer<BlockPos>, JsonDeserializer<BlockPos> {
    override fun serialize(src: BlockPos, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        return JsonPrimitive(src.toLong())
    }

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): BlockPos {
        val longValue = json.asJsonPrimitive.asLong
        return BlockPos.fromLong(longValue)
    }
}