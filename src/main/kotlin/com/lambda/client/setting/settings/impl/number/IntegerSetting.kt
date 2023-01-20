package com.lambda.client.setting.settings.impl.number

import com.google.gson.JsonElement

class IntegerSetting(
    name: String,
    value: Int,
    range: IntRange,
    step: Int,
    visibility: () -> Boolean = { true },
    consumer: (prev: Int, input: Int) -> Int = { _, input -> input },
    description: String = "",
    unit: String = "",
    fineStep: Int = step,
    formatter: (Int) -> String = { i -> "$i"},
    ) : NumberSetting<Int>(name, value, range, step, visibility, consumer, description, formatter, unit, fineStep) {

    init {
        consumers.add(0) { _, it ->
            it.coerceIn(range)
        }
    }

    override fun read(jsonElement: JsonElement?) {
        jsonElement?.asJsonPrimitive?.asInt?.let { value = it }
    }

    override fun setValue(valueIn: Double) {
        value = valueIn.toInt()
    }

}