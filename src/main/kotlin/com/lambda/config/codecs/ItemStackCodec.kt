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
import com.lambda.config.Stringifiable
import com.mojang.serialization.JsonOps
import net.minecraft.item.ItemStack
import java.lang.reflect.Type
import kotlin.jvm.optionals.getOrElse

@Suppress("unused")
object ItemStackCodec : Codec<ItemStack>, Stringifiable<ItemStack> {
    override val type = ItemStack::class.java

    override fun serialize(
        stack: ItemStack,
        typeOfSrc: Type,
        context: JsonSerializationContext
    ): JsonElement =
        ItemStack.CODEC.encodeStart(JsonOps.INSTANCE, stack)
            .orThrow

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): ItemStack =
        ItemStack.CODEC.parse(JsonOps.INSTANCE, json)
            .result()
            .getOrElse { ItemStack.EMPTY }

    override fun stringify(value: ItemStack) = value.itemName.string.uppercase()
}
