package com.lambda.client.setting.serializables

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import net.minecraft.item.Item

object ItemTypeAdapterFactory : TypeAdapterFactory {
    override fun <T : Any?> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
        @Suppress("UNNECESSARY_SAFE_CALL")
        if (!Item::class.java.isAssignableFrom(type?.rawType)) return null
        val jsonObjectAdapter = gson.getAdapter(JsonObject::class.java)

        @Suppress("UNCHECKED_CAST")
        return object : TypeAdapter<Item>() {
            override fun write(out: com.google.gson.stream.JsonWriter, value: Item?) {
                if (value == null) {
                    out.nullValue()
                    return
                }
                val jsonObject = JsonObject()
                jsonObject.addProperty("name", value.registryName.toString())
                jsonObjectAdapter.write(out, jsonObject)
            }

            override fun read(reader: JsonReader): Item? {
                val jsonObject = jsonObjectAdapter.read(reader)
                val name = jsonObject.get("name").asString
                return Item.getByNameOrId(name)
            }
        } as TypeAdapter<T>
    }
}