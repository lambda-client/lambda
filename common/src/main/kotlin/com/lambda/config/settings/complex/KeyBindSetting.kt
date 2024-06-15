package com.lambda.config.settings.complex

import com.google.gson.reflect.TypeToken
import com.lambda.config.AbstractSetting
import com.lambda.util.KeyCode

class KeyBindSetting(
    override val name: String,
    defaultValue: KeyCode,
    description: String,
    visibility: () -> Boolean,
) : AbstractSetting<KeyCode>(
    defaultValue,
    TypeToken.get(KeyCode::class.java).type,
    description,
    visibility
)
