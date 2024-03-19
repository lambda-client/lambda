package com.lambda.config.settings.numeric

import com.lambda.config.settings.NumericSetting

class ShortSetting(
    override val name: String,
    defaultValue: Short,
    override val range: ClosedRange<Short>,
    override val step: Short = 1,
    description: String,
    visibility: () -> Boolean,
    unit: String,
) : NumericSetting<Short>(
    defaultValue,
    range,
    step,
    description,
    visibility,
    unit
)