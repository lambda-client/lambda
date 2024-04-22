package com.lambda.config.settings.collections

import com.google.gson.JsonElement
import com.lambda.Lambda.gson
import com.lambda.config.AbstractSetting
import java.lang.reflect.Type

class SetSetting<T : Any>(
    override val name: String,
    private val defaultValue: MutableSet<T>,
    private val type: Type,
    description: String,
    visibility: () -> Boolean,
) : AbstractSetting<MutableSet<T>>(
    defaultValue,
    description,
    visibility
) {
    override fun loadFromJson(serialized: JsonElement) {
        value = gson.fromJson(serialized, type)
    }

    override fun toJson(): JsonElement {
        value = defaultValue.toMutableSet() // Hack the Delegates.observable
        return gson.toJsonTree(value)
    }
}
