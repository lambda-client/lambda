package com.lambda.config.serializer

import com.google.gson.*
import net.minecraft.util.math.BlockPos
import java.lang.reflect.Type

object BlockPosSerializer : JsonSerializer<BlockPos>, JsonDeserializer<BlockPos> {
    override fun serialize(
        src: BlockPos?,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement =
        src?.let {
            JsonObject().apply {
                addProperty("x", it.x)
                addProperty("y", it.y)
                addProperty("z", it.z)
            }
        } ?: JsonNull.INSTANCE

    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): BlockPos =
        json?.asJsonObject?.let {
            BlockPos(it["x"].asInt, it["y"].asInt, it["z"].asInt)
        } ?: BlockPos.ORIGIN
}