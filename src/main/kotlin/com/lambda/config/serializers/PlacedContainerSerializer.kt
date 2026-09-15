@file:Suppress("unused")

package com.lambda.config.serializers

import com.lambda.config.FallbackDeserializer
import com.lambda.config.FallbackSerializer
import com.lambda.config.JsonOps
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
import tools.jackson.databind.node.ObjectNode

object PlacedContainerSerializer : FallbackSerializer<PlacedContainer>(PlacedContainer::class.java) {
    override fun serialize(container: PlacedContainer, gen: JsonGenerator, ctxt: SerializationContext) {
        gen.writeStartObject()
        gen.writeStringProperty("Type", container.type.name)
        gen.writePOJOProperty("Pos", BlockPos.CODEC.encodeStart(JsonOps.UNCOMPRESSED, container.pos).orThrow)
        
        when (container) {
            is PlacedShulkerBoxContainer -> {
                val blockId = Registries.BLOCK.getId(container.block).toString()
                gen.writeStringProperty("Block", blockId)
            }
            is DoubleChestContainer -> {
                gen.writeBooleanProperty("Double", true)
                gen.writePOJOProperty("Left Pos", BlockPos.CODEC.encodeStart(JsonOps.UNCOMPRESSED, container.leftPos).orThrow)
                gen.writePOJOProperty("Right Pos", BlockPos.CODEC.encodeStart(JsonOps.UNCOMPRESSED, container.rightPos).orThrow)
            }
            else -> {}
        }

        gen.writeArrayPropertyStart("Stacks")
	    container.stacks.forEach { stack ->
		    val encoded = ItemStack.CODEC
			    .encodeStart(JsonOps.UNCOMPRESSED, stack)
			    .result()
		    encoded.ifPresent { gen.writePOJO(it) }
	    }
        gen.writeEndArray()
        gen.writeEndObject()
    }
}

object PlacedContainerDeserializer : FallbackDeserializer<PlacedContainer>(PlacedContainer::class.java) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): PlacedContainer? {
        val root = p.readValueAsTree<ObjectNode>() ?: return null
        
        val typeName = root.get("Type")?.asString() ?: return null
        val type = ContainerType.entries.find { it.name == typeName } ?: return null
        
        val posNode = root.get("Pos") as? ObjectNode ?: return null
        val pos = BlockPos.CODEC.parse(JsonOps.UNCOMPRESSED, posNode)
            .result()
            .orElse(null)
            ?: return null
        
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
                        val leftPosNode = root.get("Left Pos") as? ObjectNode
                        val leftPos = BlockPos.CODEC.parse(JsonOps.UNCOMPRESSED, leftPosNode)
                            .result()
                            .orElse(null)
                            ?: return null
                        val rightPosNode = root.get("Right Pos") as? ObjectNode
                        val rightPos = BlockPos.CODEC.parse(JsonOps.UNCOMPRESSED, rightPosNode)
                            .result()
                            .orElse(null)
                            ?: return null
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
}
