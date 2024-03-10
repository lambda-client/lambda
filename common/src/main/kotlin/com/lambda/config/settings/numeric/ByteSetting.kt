package com.lambda.config.settings.numeric

import com.lambda.config.settings.NumericSetting

class ByteSetting(
    override val name: String,
    defaultValue: Byte,
    override val range: ClosedRange<Byte>,
    override val step: Byte = 1,
    visibility: () -> Boolean,
    description: String
) : NumericSetting<Byte>(
    defaultValue,
    range,
    step,
    visibility,
    description
)