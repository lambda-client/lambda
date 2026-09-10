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

@file:Suppress("unused")

package com.lambda.config.serializers

import com.lambda.config.Deserializer
import com.lambda.config.JsonOps
import com.lambda.config.Serializer
import com.lambda.interaction.container.ContainerType
import com.lambda.interaction.container.containers.external.EnderChestContainer
import net.minecraft.item.ItemStack
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.JsonNodeFactory
import tools.jackson.databind.node.ObjectNode

object EnderChestContainerSerializer : Serializer<EnderChestContainer>(EnderChestContainer::class.java) {
    override fun serialize(container: EnderChestContainer, gen: JsonGenerator, ctxt: SerializationContext) {
        val root = JsonNodeFactory.instance.objectNode()
        root.put("Type", ContainerType.EnderChest.name)

        val stackArray = JsonNodeFactory.instance.arrayNode()
        for (stack in container.stacks) {
            val encoded = ItemStack.CODEC
                .encodeStart(JsonOps.UNCOMPRESSED, stack)
                .result()
            encoded.ifPresent { stackArray.add(it) }
        }
        root.set("Stacks", stackArray)

        gen.writeTree(root)
    }
}

object EnderChestContainerDeserializer : Deserializer<EnderChestContainer>(EnderChestContainer::class.java) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): EnderChestContainer {
        val root = p.readValueAsTree<ObjectNode>()
        val stackArray = root.get("Stacks") as? ArrayNode
        val stacks = stackArray?.mapNotNull { element ->
            ItemStack.CODEC
                .parse(JsonOps.UNCOMPRESSED, element)
                .result()
                .orElse(null)
        } ?: emptyList()
        
        EnderChestContainer.update(stacks)
        EnderChestContainer.scanStacksForNestedContainers()
        return EnderChestContainer
    }
}
