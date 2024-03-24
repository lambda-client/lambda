package com.lambda.config.settings.complex

import com.lambda.config.AbstractSetting
import com.lambda.util.KeyCode

class KeyBindSetting(
    override val name: String,
    defaultValue: KeyCode,
    description: String,
    visibility: () -> Boolean,
) : AbstractSetting<KeyCode>(
    defaultValue,
    description,
    visibility
)