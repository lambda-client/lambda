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
import net.minecraft.text.Text
import net.minecraft.text.TextCodecs
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.SerializationContext

object TextSerializer : Serializer<Text>(Text::class.java) {
    override fun serialize(text: Text, gen: JsonGenerator, ctxt: SerializationContext) {
        gen.writeTree(TextCodecs.CODEC.encodeStart(JsonOps.Uncompressed, text).orThrow)
    }
}

object TextDeserializer : Deserializer<Text>(Text::class.java) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Text =
        TextCodecs.CODEC.parse(JsonOps.Uncompressed, mapper.readTree(p)).orThrow
}