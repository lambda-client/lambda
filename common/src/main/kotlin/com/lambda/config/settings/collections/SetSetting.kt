package com.lambda.config.settings.collections

import com.google.gson.JsonElement
import com.lambda.config.AbstractSetting
import java.lang.reflect.Type

class SetSetting<T : Any>(
    override val name: String,
    private val defaultValue: MutableSet<T>,
    type: Type,
    description: String,
    private val hackDelegates: Boolean,
    visibility: () -> Boolean,
) : AbstractSetting<MutableSet<T>>(
    defaultValue,
    type,
    description,
    visibility
) {
    override fun toJson(): JsonElement {
        if (hackDelegates) value = defaultValue
        return super.toJson()
    }
}
