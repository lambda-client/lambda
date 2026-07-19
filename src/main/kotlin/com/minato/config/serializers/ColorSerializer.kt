
@file:Suppress("unused")

package com.minato.config.serializers

import com.minato.config.Deserializer
import com.minato.config.Serializer
import com.minato.config.Stringifiable
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.SerializationContext
import java.awt.Color

object ColorSerializer : Serializer<Color>(Color::class.java), Stringifiable<Color> {
    override fun serialize(color: Color, gen: JsonGenerator, ctxt: SerializationContext) {
        gen.writeString("${color.red},${color.green},${color.blue},${color.alpha}")
    }

    override fun stringify(value: Color) = "${value.red},${value.green},${value.blue},${value.alpha}"
}

object ColorDeserializer : Deserializer<Color>(Color::class.java) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Color {
        val color = p.valueAsString.split(",")
        return when (color.size) {
            3 -> Color(color[0].toInt(), color[1].toInt(), color[2].toInt())
            4 -> Color(color[0].toInt(), color[1].toInt(), color[2].toInt(), color[3].toInt())
            else -> throw IllegalStateException("Invalid color format")
        }
    }
}