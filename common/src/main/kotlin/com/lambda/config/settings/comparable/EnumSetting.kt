package com.lambda.config.settings.comparable

import com.lambda.config.AbstractSetting

class EnumSetting<T : Enum<T>>(
    override val name: String,
    defaultValue: T,
    visibility: () -> Boolean,
    description: String,
) : AbstractSetting<T>(
    defaultValue,
    visibility,
    description,
) {
    private val enumValues: Array<T> = defaultValue.declaringJavaClass.enumConstants

    fun next() {
        value = enumValues[((value.ordinal + 1) % enumValues.size)]
    }
}