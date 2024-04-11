package com.lambda.config.settings.collections

import com.google.gson.JsonElement
import com.lambda.Lambda.gson
import com.lambda.config.AbstractSetting
import java.lang.reflect.Type

class MapSetting<K, V>(
    override val name: String,
    defaultValue: Map<K, V>,
    private val type: Type,
    description: String,
    visibility: () -> Boolean,
) : AbstractSetting<Map<K, V>>(
    defaultValue,
    description,
    visibility
) {
    override fun loadFromJson(serialized: JsonElement) {
        value = gson.fromJson(serialized, type)
    }
}