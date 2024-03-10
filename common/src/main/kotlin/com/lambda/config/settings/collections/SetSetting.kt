package com.lambda.config.settings.collections

import com.lambda.config.AbstractSetting

class SetSetting<T : Any>(
    override val name: String,
    defaultValue: Set<T>,
    visibility: () -> Boolean,
    description: String
) : AbstractSetting<Set<T>>(
    defaultValue,
    visibility,
    description
)