package com.lambda.config.settings.complex

import com.lambda.config.AbstractSetting
import java.awt.Color

class ColorSetting(
    override val name: String,
    defaultValue: Color,
    description: String,
    visibility: () -> Boolean,
) : AbstractSetting<Color>(
    defaultValue,
    description,
    visibility
)
