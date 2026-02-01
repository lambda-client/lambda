/*
 * Copyright 2025 Lambda
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

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonElement
import com.google.gson.JsonSerializationContext
import com.lambda.config.Codec
import com.lambda.config.Stringifiable
import com.mojang.serialization.JsonOps
import net.minecraft.block.Block
import net.minecraft.registry.Registries
import java.lang.reflect.Type

object BlockCodec : Codec<Block>, Stringifiable<Block> {
	override fun serialize(
		src: Block,
		typeOfSrc: Type,
		context: JsonSerializationContext,
	): JsonElement =
		Registries.BLOCK.codec.encodeStart(JsonOps.INSTANCE, src)
			.orThrow

	override fun deserialize(
		json: JsonElement?,
		typeOfT: Type?,
		context: JsonDeserializationContext?,
	): Block =
		Registries.BLOCK.codec.parse(JsonOps.INSTANCE, json)
			.orThrow

	override fun stringify(value: Block) = Registries.BLOCK.getId(value).path.replaceFirstChar { it.uppercase() }
}
