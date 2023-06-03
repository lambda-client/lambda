package com.lambda.client.setting.serializables

import com.google.gson.*
import net.minecraft.item.Item
import java.lang.reflect.Type

object ItemSerializer : JsonSerializer<Item>, JsonDeserializer<Item> {
    override fun serialize(src: Item, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        return JsonPrimitive(src.registryName.toString())
    }

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Item? {
        val stringValue = json.asJsonPrimitive.asString
        return Item.getByNameOrId(stringValue)
    }
}