
@file:Suppress("unused")

package com.minato.config.serializers

import com.minato.config.Deserializer
import com.minato.config.JsonOps
import com.minato.config.Serializer
import com.minato.config.Stringifiable
import net.minecraft.block.Block
import net.minecraft.registry.Registries
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.SerializationContext

object BlockSerializer : Serializer<Block>(Block::class.java), Stringifiable<Block> {
    override fun serialize(block: Block, gen: JsonGenerator, ctxt: SerializationContext) {
        gen.writeTree((Registries.BLOCK.codec.encodeStart(JsonOps.UNCOMPRESSED, block).orThrow))
    }

    override fun stringify(value: Block) = Registries.BLOCK.getId(value).path.replaceFirstChar { it.uppercase() }
}

object BlockDeserializer : Deserializer<Block>(Block::class.java) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Block =
        Registries.BLOCK.codec.parse(JsonOps.UNCOMPRESSED, p.readValueAsTree()).orThrow
}