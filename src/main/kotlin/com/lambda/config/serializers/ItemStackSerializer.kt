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

import com.lambda.config.JsonOps
import com.lambda.config.Serializer
import com.lambda.config.Stringifiable
import net.minecraft.item.ItemStack
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.deser.std.StdDeserializer
import tools.jackson.databind.ser.std.StdSerializer

@Suppress("unused")
object ItemStackSerializer : Serializer<ItemStack>(), Stringifiable<ItemStack> {
    override val type = ItemStack::class.java

    override val serializer = object : StdSerializer<ItemStack>(type) {
        override fun serialize(itemStack: ItemStack, gen: JsonGenerator, ctxt: SerializationContext) {
            gen.writeTree(ItemStack.CODEC.encodeStart(JsonOps.Uncompressed, itemStack).orThrow)
        }
    }

    override val deSerializer = object : StdDeserializer<ItemStack>(type) {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext) =
            ItemStack.CODEC.parse(JsonOps.Uncompressed, p.readValueAsTree()).orThrow
    }

    override fun stringify(value: ItemStack) = value.itemName.string.uppercase()
}