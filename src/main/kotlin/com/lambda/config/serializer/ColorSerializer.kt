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

package com.lambda.config.serializer

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.lambda.config.Codec
import com.lambda.config.Stringifiable
import java.awt.Color
import java.lang.reflect.Type

object ColorSerializer : Codec<Color>, Stringifiable<Color> {
    override fun serialize(
        src: Color,
        typeOfSrc: Type,
        context: JsonSerializationContext?,
    ): JsonElement =
        JsonPrimitive("${src.red},${src.green},${src.blue},${src.alpha}")

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext?,
    ): Color =
        json.asString.split(",").let {
            when (it.size) {
                3 -> Color(it[0].toInt(), it[1].toInt(), it[2].toInt())
                4 -> Color(it[0].toInt(), it[1].toInt(), it[2].toInt(), it[3].toInt())
                else -> throw JsonParseException("Invalid color format")
            }
        }

    override fun stringify(value: Color) = "${value.red},${value.green},${value.blue},${value.alpha}"
}
