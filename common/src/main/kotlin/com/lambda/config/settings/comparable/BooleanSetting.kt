package com.lambda.config.settings.comparable

import com.lambda.config.AbstractSetting

class BooleanSetting(
    override val name: String,
    defaultValue: Boolean,
    visibility: () -> Boolean,
    description: String
) : AbstractSetting<Boolean>(
    defaultValue,
    visibility,
    description
)