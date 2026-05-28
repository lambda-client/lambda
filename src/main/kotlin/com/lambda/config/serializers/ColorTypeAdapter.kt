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

import com.fasterxml.jackson.core.JsonParseException
import com.lambda.config.Stringifiable
import com.lambda.config.TypeAdapter
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.SerializationContext
import java.awt.Color

@Suppress("unused")
object ColorTypeAdapter : TypeAdapter<Color>(), Stringifiable<Color> {
    override val type = Color::class.java

    override val serializer = object : Serializer<Color>(type) {
        override fun serialize(color: Color, gen: JsonGenerator, ctxt: SerializationContext) {
            gen.writeString("${color.red},${color.green},${color.blue},${color.alpha}")
        }
    }

    override val deserializer = object : Deserializer<Color>(type) {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Color {
            val color = p.valueAsString.split(",")
            return when (color.size) {
                3 -> Color(color[0].toInt(), color[1].toInt(), color[2].toInt())
                4 -> Color(color[0].toInt(), color[1].toInt(), color[2].toInt(), color[3].toInt())
                else -> throw JsonParseException("Invalid color format")
            }
        }
    }

    override fun stringify(value: Color) = "${value.red},${value.green},${value.blue},${value.alpha}"
}
