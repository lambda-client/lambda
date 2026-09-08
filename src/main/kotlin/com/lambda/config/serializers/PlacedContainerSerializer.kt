@file:Suppress("unused")

package com.lambda.config.serializers

import com.lambda.config.Deserializer
import com.lambda.config.JsonOps
import com.lambda.config.Serializer
import com.lambda.interaction.container.ContainerType
import com.lambda.interaction.container.PlacedContainer
import com.lambda.interaction.container.containers.external.ChestContainer
import com.lambda.interaction.container.containers.external.DoubleChestContainer
import com.lambda.interaction.container.containers.external.PlacedShulkerBoxContainer
import net.minecraft.block.Blocks
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.JsonNodeFactory
import tools.jackson.databind.node.ObjectNode

object PlacedContainerSerializer : Serializer<PlacedContainer>(PlacedContainer::class.java) {
    override fun serialize(container: PlacedContainer, gen: JsonGenerator, ctxt: SerializationContext) {
        val root = JsonNodeFactory.instance.objectNode()
        
        root.put("Type", container.type.name)
        root.set("Pos", blockPosNode(container.pos))
        
        when (container) {
            is PlacedShulkerBoxContainer -> {
                val blockId = Registries.BLOCK.getId(container.block).toString()
                root.put("Block", blockId)
            }
            is DoubleChestContainer -> {
                root.put("Double", true)
                root.set("Left Pos", blockPosNode(container.leftPos))
                root.set("Right Pos", blockPosNode(container.rightPos))
            }
            else -> {}
        }
        
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

    private fun blockPosNode(pos: BlockPos): ObjectNode {
        val node = JsonNodeFactory.instance.objectNode()
        node.put("X", pos.x)
        node.put("Y", pos.y)
        node.put("Z", pos.z)
        return node
    }
}

object PlacedContainerDeserializer : Deserializer<PlacedContainer>(PlacedContainer::class.java) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): PlacedContainer? {
        val root = p.readValueAsTree<ObjectNode>() ?: return null
        
        val typeName = root.get("Type")?.asString() ?: return null
        val type = ContainerType.entries.find { it.name == typeName } ?: return null
        
        val posNode = root.get("Pos") as? ObjectNode ?: return null
        val pos = parseBlockPos(posNode) ?: return null
        
        val stackArray = root.get("Stacks") as? ArrayNode
        val stacks =
            stackArray?.mapNotNull { element ->
                ItemStack.CODEC
                    .parse(JsonOps.UNCOMPRESSED, element)
                    .result()
                    .orElse(null)
            } ?: emptyList()
        
        val container =
            when (type) {
                ContainerType.PlacedShulkerBox -> {
                    val blockId = root.get("Block")?.asString()
                    val block = blockId?.let { Registries.BLOCK.get(Identifier.of(it)) } ?: Blocks.SHULKER_BOX
                    PlacedShulkerBoxContainer(pos, block, null, stacks)
                }
                ContainerType.Chest -> {
                    if (root.get("Double")?.asBoolean() == true) {
                        val leftPos = (root.get("Left Pos") as? ObjectNode)?.let { parseBlockPos(it) } ?: pos
                        val rightPos = (root.get("Right Pos") as? ObjectNode)?.let { parseBlockPos(it) } ?: pos
                        DoubleChestContainer(pos, leftPos, rightPos, stacks)
                    } else {
                        ChestContainer(pos, stacks)
                    }
                }
                else -> return null
            }
        
        container.scanStacksForNestedContainers()
        return container
    }

    private fun parseBlockPos(node: ObjectNode): BlockPos? {
        val x = node.get("X")?.asInt() ?: return null
        val y = node.get("Y")?.asInt() ?: return null
        val z = node.get("Z")?.asInt() ?: return null
        return BlockPos(x, y, z)
    }
}
