
@file:Suppress("unused")

package com.minato.config.serializers

import com.minato.config.Deserializer
import com.minato.config.Serializer
import com.minato.util.KeyCode
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.SerializationContext

object KeyCodeSerializer : Serializer<KeyCode>(KeyCode::class.java) {
    override fun serialize(keyCode: KeyCode, gen: JsonGenerator, ctxt: SerializationContext) {
        gen.writeString(keyCode.name)
    }
}

object KeyCodeDeserializer : Deserializer<KeyCode>(KeyCode::class.java) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): KeyCode =
        KeyCode.fromKeyName(p.string)
}