/*
 * Copyright 2026 Lambda
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

package com.lambda.config.codecs

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.lambda.config.Codec
import com.lambda.config.Stringifiable
import net.minecraft.item.Item
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier
import java.lang.reflect.Type

object ItemCodec : Codec<Item>, Stringifiable<Item> {
	override val type = Item::class.java

	override fun serialize(
		item: Item,
		typeOfSrc: Type,
		context: JsonSerializationContext
	): JsonElement = JsonPrimitive(item.toString())

	override fun deserialize(
		json: JsonElement,
		typeOfT: Type,
		context: JsonDeserializationContext
	): Item =
		Registries.ITEM.get(Identifier.of(json.asString)) // Watch out!! Errors are silently catched by gson!!

	override fun stringify(value: Item) = value.name.string.replaceFirstChar { it.uppercase() }
}