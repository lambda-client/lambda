/*
 * Copyright 2024 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.config.serializer

import com.google.gson.*
import net.minecraft.util.math.BlockPos
import java.lang.reflect.Type

object BlockPosSerializer : JsonSerializer<BlockPos>, JsonDeserializer<BlockPos> {
    override fun serialize(
        src: BlockPos?,
        typeOfSrc: Type?,
        context: JsonSerializationContext?,
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
        context: JsonDeserializationContext?,
    ): BlockPos =
        json?.asJsonObject?.let {
            BlockPos(it["x"].asInt, it["y"].asInt, it["z"].asInt)
        } ?: BlockPos.ORIGIN
}
