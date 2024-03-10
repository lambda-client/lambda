package com.lambda.config.settings.collections

import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import com.lambda.Lambda.gson
import com.lambda.config.AbstractSetting

class SetSetting<T : Any>(
    override val name: String,
    defaultValue: Set<T>,
    visibility: () -> Boolean,
    description: String
) : AbstractSetting<Set<T>>(
    defaultValue,
    visibility,
    description
) {
    override fun loadFromJson(serialized: JsonElement) {
        val setType = object : TypeToken<Set<T>>() {}.type
        value = gson.fromJson(serialized, setType)
    }
}