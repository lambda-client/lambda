package com.lambda.client.setting.settings.impl.other

import com.lambda.client.setting.settings.MutableSetting
import com.lambda.client.util.color.ColorHolder

class ColorSetting(
    name: String,
    value: ColorHolder,
    val hasAlpha: Boolean = true,
    visibility: () -> Boolean = { true },
    description: String = ""
) : MutableSetting<ColorHolder>(name, value, visibility, { _, input -> if (!hasAlpha) input.apply { a = 255 } else input }, description, unit = "")