
@file:Suppress("unused")

package com.minato.config.serializers

import com.minato.config.Deserializer
import com.minato.config.JsonOps
import com.minato.config.Serializer
import net.minecraft.text.Text
import net.minecraft.text.TextCodecs
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.SerializationContext

object TextSerializer : Serializer<Text>(Text::class.java) {
    override fun serialize(text: Text, gen: JsonGenerator, ctxt: SerializationContext) {
        gen.writeTree(TextCodecs.CODEC.encodeStart(JsonOps.UNCOMPRESSED, text).orThrow)
    }
}

object TextDeserializer : Deserializer<Text>(Text::class.java) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Text =
        TextCodecs.CODEC.parse(JsonOps.UNCOMPRESSED, mapper.readTree(p)).orThrow
}