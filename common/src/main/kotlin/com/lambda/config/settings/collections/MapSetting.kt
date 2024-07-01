package com.lambda.config.settings.collections

import com.google.gson.JsonElement
import com.lambda.config.AbstractSetting
import java.lang.reflect.Type

class MapSetting<K, V>(
    override val name: String,
    private val defaultValue: Map<K, V>,
    type: Type,
    description: String,
    private val hackDelegates: Boolean,
    visibility: () -> Boolean,
) : AbstractSetting<Map<K, V>>(
    defaultValue,
    type,
    description,
    visibility
) {
    override fun toJson(): JsonElement {
        if (hackDelegates) value = defaultValue
        return super.toJson()
    }
}
