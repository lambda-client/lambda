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
import com.lambda.config.Serializer
import com.lambda.config.Stringifiable
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