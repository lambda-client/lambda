package com.lambda.client.setting.settings.impl.primitive

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.lambda.client.setting.settings.MutableSetting

open class BooleanSetting(
    name: String,
    value: Boolean,
    visibility: () -> Boolean = { true },
    consumer: (prev: Boolean, input: Boolean) -> Boolean = { _, input -> input },
    description: String = "",
    formatter: (Boolean) -> String = { b -> "$b"},
    ) : MutableSetting<Boolean>(name, value, visibility, consumer, description, formatter, unit = "") {

    override fun write(): JsonElement = JsonPrimitive(value)

    override fun read(jsonElement: JsonElement?) {
        jsonElement?.asJsonPrimitive?.asBoolean?.let { value = it }
    }

}