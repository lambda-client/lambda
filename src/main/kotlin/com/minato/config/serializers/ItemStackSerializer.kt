
@file:Suppress("unused")

package com.minato.config.serializers

import com.minato.config.Deserializer
import com.minato.config.JsonOps
import com.minato.config.Serializer
import com.minato.config.Stringifiable
import net.minecraft.item.ItemStack
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.SerializationContext

object ItemStackSerializer : Serializer<ItemStack>(ItemStack::class.java), Stringifiable<ItemStack> {
    override fun serialize(itemStack: ItemStack, gen: JsonGenerator, ctxt: SerializationContext) {
        gen.writeTree(ItemStack.CODEC.encodeStart(JsonOps.UNCOMPRESSED, itemStack).orThrow)
    }

    override fun stringify(value: ItemStack) = value.itemName.string.uppercase()
}

object ItemStackDeserializer : Deserializer<ItemStack>(ItemStack::class.java) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): ItemStack =
        ItemStack.CODEC.parse(JsonOps.UNCOMPRESSED, mapper.readTree(p)).orThrow
}