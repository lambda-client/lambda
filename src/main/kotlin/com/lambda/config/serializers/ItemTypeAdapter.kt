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

package com.lambda.config.serializers

import com.lambda.config.Stringifiable
import com.lambda.config.TypeAdapter
import net.minecraft.item.Item
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.SerializationContext

object ItemTypeAdapter : TypeAdapter<Item>(), Stringifiable<Item> {
	override val type = Item::class.java

	override val serializer = object : Serializer<Item>(type) {
		override fun serialize(item: Item, gen: JsonGenerator, ctxt: SerializationContext) {
			gen.writeString(item.toString())
		}
	}

	override val deserializer = object : Deserializer<Item>(type) {
		override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Item {
			return Registries.ITEM.get(Identifier.of(p.valueAsString))
		}
	}

	override fun stringify(value: Item) = value.name.string.replaceFirstChar { it.uppercase() }
}