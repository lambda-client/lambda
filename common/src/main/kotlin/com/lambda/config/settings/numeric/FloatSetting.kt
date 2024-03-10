package com.lambda.config.settings.numeric

import com.lambda.config.settings.NumericSetting

class FloatSetting(
    override val name: String,
    defaultValue: Float,
    override val range: ClosedRange<Float>,
    override val step: Float = 1f,
    visibility: () -> Boolean,
    description: String
) : NumericSetting<Float>(
    defaultValue,
    range,
    step,
    visibility,
    description
)