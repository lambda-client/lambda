package com.lambda.client.setting.settings.impl.collection

import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import com.lambda.client.setting.settings.ImmutableSetting

class MapSetting<K : Any, V : Any, T : MutableMap<K, V>>(
    name: String,
    override val value: T,
    visibility: () -> Boolean = { true },
    description: String = "",
    unit: String = ""
) : ImmutableSetting<T>(name, value, visibility, { _, input -> input }, description, unit) {
    override val defaultValue: T = valueClass.newInstance()
    private val type = object : TypeToken<Map<K, V>>() {}.type

    init {
        value.toMap(defaultValue)
    }

    override fun resetValue() {
        value.clear()
        value.putAll(defaultValue)
    }

    override fun write(): JsonElement = gson.toJsonTree(value)

    override fun read(jsonElement: JsonElement?) {
        jsonElement?.let {
            val cacheMap = gson.fromJson<Map<K, V>>(it, type)
            value.clear()
            value.putAll(cacheMap)
        }
    }

    override fun toString() = value.entries.joinToString { "${it.key} to ${it.value}" }
}