
@file:Suppress("unused")

package com.minato.config.serializers

import com.minato.config.Deserializer
import com.minato.config.Serializer
import com.minato.config.Stringifiable
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.SerializationContext
import java.util.*

object UuidSerializer : Serializer<UUID>(UUID::class.java), Stringifiable<UUID> {
    override fun serialize(uuid: UUID, gen: JsonGenerator, ctxt: SerializationContext) {
        gen.writeString(uuid.toString())
    }

    override fun stringify(value: UUID) = value.toString()
}

object UuidDeserializer : Deserializer<UUID>(UUID::class.java) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): UUID {
        val jsonNode = mapper.readTree(p)

        val rawId = when {
            jsonNode.isString -> jsonNode.stringValue()
            jsonNode.isObject && jsonNode.has("id") -> jsonNode.get("id").stringValue()
            else -> throw IllegalStateException("Cannot deserialize UUID from: $jsonNode")
        }

        val parsedId = if (rawId.length == 32)
            rawId.replace(Regex("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})"), "$1-$2-$3-$4-$5")
        else rawId

        return UUID.fromString(parsedId)
    }
}