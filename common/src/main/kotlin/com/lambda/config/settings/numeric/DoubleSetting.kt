package com.lambda.config.settings.numeric


import com.lambda.config.settings.NumericSetting

class DoubleSetting(
    override val name: String,
    defaultValue: Double,
    override val range: ClosedRange<Double>,
    override val step: Double = 1.0,
    visibility: () -> Boolean,
    description: String
) : NumericSetting<Double>(
    defaultValue,
    range,
    step,
    visibility,
    description
)
