package com.lambda.config.settings.comparable

import com.google.gson.reflect.TypeToken
import com.lambda.config.AbstractSetting

class EnumSetting<T : Enum<T>>(
    override val name: String,
    defaultValue: T,
    description: String,
    visibility: () -> Boolean,
) : AbstractSetting<T>(
    defaultValue,
    TypeToken.get(defaultValue.declaringJavaClass).type,
    description,
    visibility,
) {
    val enumValues: Array<T> = defaultValue.declaringJavaClass.enumConstants

    fun next() {
        value = enumValues[((value.ordinal + 1) % enumValues.size)]
    }
}
