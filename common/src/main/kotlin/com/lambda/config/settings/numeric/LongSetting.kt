package com.lambda.config.settings.numeric

import com.lambda.config.settings.NumericSetting

class LongSetting(
    override val name: String,
    defaultValue: Long,
    override val range: ClosedRange<Long>,
    override val step: Long = 1,
    visibility: () -> Boolean,
    description: String
) : NumericSetting<Long>(
    defaultValue,
    range,
    step,
    visibility,
    description
)