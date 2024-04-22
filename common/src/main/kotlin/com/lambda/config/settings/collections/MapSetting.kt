package com.lambda.config.settings.collections

import com.google.gson.JsonElement
import com.lambda.Lambda.gson
import com.lambda.config.AbstractSetting
import java.lang.reflect.Type

class MapSetting<K, V>(
    override val name: String,
    private val defaultValue: MutableMap<K, V>,
    private val type: Type,
    description: String,
    visibility: () -> Boolean,
) : AbstractSetting<MutableMap<K, V>>(
    defaultValue,
    description,
    visibility
) {
    override fun loadFromJson(serialized: JsonElement) {
        value = gson.fromJson(serialized, type)
    }

    override fun toJson(): JsonElement {
        value = defaultValue.toMutableMap() // Hack the Delegates.observable
        return gson.toJsonTree(value)
    }
}
