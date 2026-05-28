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
import com.lambda.config.Stringifiable
import com.lambda.config.TypeAdapter
import net.minecraft.block.Block
import net.minecraft.registry.Registries
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.SerializationContext

object BlockTypeAdapter : TypeAdapter<Block>(), Stringifiable<Block> {
    override val type = Block::class.java

    override val serializer = object : Serializer<Block>(type) {
        override fun serialize(block: Block, gen: JsonGenerator, ctxt: SerializationContext) {
            gen.writeTree((Registries.BLOCK.codec.encodeStart(JsonOps.Uncompressed, block).orThrow))
        }
    }

    override val deserializer = object : Deserializer<Block>(type) {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext) =
            Registries.BLOCK.codec.parse(JsonOps.Uncompressed, p.readValueAsTree()).orThrow
    }

    override fun stringify(value: Block) = Registries.BLOCK.getId(value).path.replaceFirstChar { it.uppercase() }
}
