package com.lambda.config.settings.collections

import com.google.common.reflect.TypeToken
import com.google.gson.JsonElement
import com.lambda.Lambda.gson
import com.lambda.config.AbstractSetting

class MapSetting<K, V>(
    override val name: String,
    defaultValue: Map<K, V>,
    description: String,
    visibility: () -> Boolean,
) : AbstractSetting<Map<K, V>>(
    defaultValue,
    description,
    visibility
) {
    override fun loadFromJson(serialized: JsonElement) {
        val mapType = object : TypeToken<Map<K, V>>() {}.type
        value = gson.fromJson(serialized, mapType)
    }
}