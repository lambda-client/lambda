package com.lambda.config.settings.numeric


import com.lambda.config.settings.NumericSetting

class IntegerSetting(
    override val name: String,
    defaultValue: Int,
    override val range: ClosedRange<Int>,
    override val step: Int = 1,
    description: String,
    visibility: () -> Boolean,
    unit: String,
) : NumericSetting<Int>(
    defaultValue,
    range,
    step,
    description,
    visibility,
    unit
)