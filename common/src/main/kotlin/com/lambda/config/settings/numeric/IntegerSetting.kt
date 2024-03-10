package com.lambda.config.settings.numeric


import com.lambda.config.settings.NumericSetting

class IntegerSetting(
    override val name: String,
    defaultValue: Int,
    override val range: ClosedRange<Int>,
    override val step: Int = 1,
    visibility: () -> Boolean,
    description: String,
) : NumericSetting<Int>(
    defaultValue,
    range,
    step,
    visibility,
    description
)