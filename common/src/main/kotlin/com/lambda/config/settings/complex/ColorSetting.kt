package com.lambda.config.settings.complex

import com.google.gson.reflect.TypeToken
import com.lambda.config.AbstractSetting
import java.awt.Color

class ColorSetting(
    override val name: String,
    defaultValue: Color,
    description: String,
    visibility: () -> Boolean,
) : AbstractSetting<Color>(
    defaultValue,
    TypeToken.get(Color::class.java).type,
    description,
    visibility
)
