package com.lambda.config.settings.collections

import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import com.lambda.Lambda.gson
import com.lambda.config.AbstractSetting

class ListSetting<T>(
    override val name: String,
    defaultValue: List<T>,
    description: String,
    visibility: () -> Boolean,
) : AbstractSetting<List<T>>(
    defaultValue,
    description,
    visibility
) {
    override fun loadFromJson(serialized: JsonElement) {
        val listType = object : TypeToken<List<T>>() {}.type
        value = gson.fromJson(serialized, listType)
    }
}