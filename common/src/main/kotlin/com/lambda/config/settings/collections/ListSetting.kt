package com.lambda.config.settings.collections

import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import com.lambda.Lambda.gson
import com.lambda.config.AbstractSetting
import java.lang.reflect.Type
import java.util.ArrayList

class ListSetting<T : Any>(
    override val name: String,
    defaultValue: ArrayList<T>,
    private val type: Type,
    description: String,
    visibility: () -> Boolean,
) : AbstractSetting<ArrayList<T>>(
    defaultValue,
    description,
    visibility
) {
    override fun loadFromJson(serialized: JsonElement) {
        value = gson.fromJson(serialized, type)
    }
}
