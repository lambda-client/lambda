package com.lambda.config.settings.collections

import com.lambda.config.AbstractSetting

class ListSetting<T>(
    override val name: String,
    defaultValue: List<T>,
    visibility: () -> Boolean,
    description: String
) : AbstractSetting<List<T>>(
    defaultValue,
    visibility,
    description
)