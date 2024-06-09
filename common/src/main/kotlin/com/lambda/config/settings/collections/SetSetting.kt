package com.lambda.config.settings.collections

import com.lambda.config.AbstractSetting
import java.lang.reflect.Type

class SetSetting<T : Any>(
    override val name: String,
    defaultValue: MutableSet<T>,
    type: Type,
    description: String,
    visibility: () -> Boolean,
) : AbstractSetting<MutableSet<T>>(
    defaultValue,
    type,
    description,
    visibility
)
