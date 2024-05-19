package com.lambda.config.settings.comparable

import com.lambda.config.AbstractSetting
import com.lambda.util.Nameable
import java.util.*

class EnumSetting<T : Enum<T>>(
    override val name: String,
    defaultValue: T,
    description: String,
    visibility: () -> Boolean,
) : AbstractSetting<T>(
    defaultValue,
    description,
    visibility,
) {
    val displayValue get() = (value as? Nameable)?.name ?: value.name.split('_').joinToString(" ") { low ->
        low.lowercase().replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }
    }

    val enumValues: Array<T> = defaultValue.declaringJavaClass.enumConstants

    fun next() {
        value = enumValues[((value.ordinal + 1) % enumValues.size)]
    }
}