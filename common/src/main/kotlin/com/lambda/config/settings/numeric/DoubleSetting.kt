package com.lambda.config.settings.numeric


import com.lambda.config.settings.NumericSetting

class DoubleSetting(
    override val name: String,
    defaultValue: Double,
    override val range: ClosedRange<Double>,
    override val step: Double,
    description: String,
    visibility: () -> Boolean,
    unit: String,
) : NumericSetting<Double>(
    defaultValue,
    range,
    step,
    description,
    visibility,
    unit
)
