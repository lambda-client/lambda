package com.lambda.config.settings.collections

import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import com.lambda.Lambda
import com.lambda.Lambda.LOG
import com.lambda.Lambda.gson
import com.lambda.config.AbstractSetting
import java.lang.reflect.Type

class ListSetting<T>(
    override val name: String,
    defaultValue: List<T>,
    visibility: () -> Boolean,
    description: String
) : AbstractSetting<List<T>>(
    defaultValue,
    visibility,
    description
) {
    override fun loadFromJson(serialized: JsonElement) {
        val listType = object : TypeToken<List<T>>() {}.type
        value = gson.fromJson(serialized, listType)
    }
}