package com.lambda.client.setting.settings.impl.primitive

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.lambda.client.setting.settings.MutableSetting

class StringSetting(
    name: String,
    value: String,
    visibility: () -> Boolean = { true },
    consumer: (prev: String, input: String) -> String = { _, input -> input },
    description: String = "",
    formatter: (String) -> String = { s -> s },
    ) : MutableSetting<String>(name, value, visibility, consumer, description, formatter, unit = "") {

    override fun setValue(valueIn: String) {
        value = valueIn
    }

    override fun write() = JsonPrimitive(value)

    override fun read(jsonElement: JsonElement?) {
        jsonElement?.asJsonPrimitive?.asString?.let { value = it }
    }

}