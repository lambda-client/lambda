package com.lambda.config.settings.collections

import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import com.lambda.Lambda.gson
import com.lambda.config.AbstractSetting

class ListSetting<T>(
    override val name: String,
    defaultValue: MutableList<T>,
    description: String,
    visibility: () -> Boolean,
) : AbstractSetting<MutableList<T>>(
    defaultValue,
    description,
    visibility
) {
    override fun loadFromJson(serialized: JsonElement) {
        val listType = object : TypeToken<MutableList<T>>() {}.type
        value = gson.fromJson(serialized, listType)
    }
}