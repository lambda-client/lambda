
@file:Suppress("unused")

package com.minato.config.serializers

import com.minato.config.Deserializer
import com.minato.config.Serializer
import com.minato.config.Stringifiable
import net.minecraft.item.Item
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.SerializationContext

object ItemSerializer : Serializer<Item>(Item::class.java), Stringifiable<Item> {
	override fun serialize(item: Item, gen: JsonGenerator, ctxt: SerializationContext) {
		gen.writeString(item.toString())
	}

	override fun stringify(value: Item) = value.name.string.replaceFirstChar { it.uppercase() }
}

object ItemDeserializer : Deserializer<Item>(Item::class.java) {
	override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Item {
		return Registries.ITEM.get(Identifier.of(p.valueAsString))
	}
}