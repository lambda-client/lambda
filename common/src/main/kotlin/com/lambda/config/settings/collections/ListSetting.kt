package com.lambda.config.settings.collections

import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import com.lambda.Lambda.LOG
import com.lambda.Lambda.gson
import com.lambda.config.AbstractSetting
import com.lambda.util.DynamicReflectionSerializer.dynamicString

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
        LOG.info("Loading $name with value $serialized current value ${value.dynamicString()} $value and type ${listType.typeName}")
        val dese = gson.fromJson<List<T>>(serialized, listType)
        value = dese
        LOG.info("Loaded $name with value ${value.dynamicString()} $value and type ${listType.typeName}")
    }
}