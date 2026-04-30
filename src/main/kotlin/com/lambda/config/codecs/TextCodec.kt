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

package com.lambda.config.codecs

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonElement
import com.google.gson.JsonSerializationContext
import com.lambda.config.Codec
import com.mojang.serialization.JsonOps
import net.minecraft.text.Text
import net.minecraft.text.TextCodecs
import java.lang.reflect.Type
import kotlin.jvm.optionals.getOrElse

@Suppress("unused")
object TextCodec : Codec<Text> {
    override val type = Text::class.java

    override fun serialize(
        src: Text,
        typeOfSrc: Type,
        context: JsonSerializationContext,
    ): JsonElement =
        TextCodecs.CODEC.encodeStart(JsonOps.INSTANCE, src)
            .orThrow

    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?,
    ): Text =
        TextCodecs.CODEC.parse(JsonOps.INSTANCE, json)
            .result()
            .getOrElse { Text.empty() }
}
