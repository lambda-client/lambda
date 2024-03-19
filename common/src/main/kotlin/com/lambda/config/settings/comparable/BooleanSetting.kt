package com.lambda.config.settings.comparable

import com.lambda.config.AbstractSetting

class BooleanSetting(
    override val name: String,
    defaultValue: Boolean,
    description: String,
    visibility: () -> Boolean,
) : AbstractSetting<Boolean>(
    defaultValue,
    description,
    visibility
)