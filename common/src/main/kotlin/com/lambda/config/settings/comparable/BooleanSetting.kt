package com.lambda.config.settings.comparable

import com.google.gson.reflect.TypeToken
import com.lambda.config.AbstractSetting

class BooleanSetting(
    override val name: String,
    defaultValue: Boolean,
    description: String,
    visibility: () -> Boolean,
) : AbstractSetting<Boolean>(
    defaultValue,
    TypeToken.get(Boolean::class.java).type,
    description,
    visibility
)
