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
        Registries.BLOCK.get(Identifier.of(json?.asString))
}
