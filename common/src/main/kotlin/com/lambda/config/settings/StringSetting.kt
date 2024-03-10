package com.lambda.config.settings

import com.lambda.config.AbstractSetting

class StringSetting(
    override val name: String,
    defaultValue: String,
    visibility: () -> Boolean,
    description: String
) : AbstractSetting<String>(
    defaultValue,
    visibility,
    description
)