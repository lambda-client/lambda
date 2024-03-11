package com.lambda.config.settings.numeric

import com.lambda.config.settings.NumericSetting

/**
 * Represents a [NumericSetting] for [Byte] values with a specific [range] and [step].
 *
 * The [value] of the setting is coerced into the specified [range] and rounded to the nearest [step].
 * The [visibility] and [description] of the setting are inherited from [NumericSetting].
 *
 * @property name The name of the setting.
 * @property range The range within which the setting's [value] must fall.
 * @property step The [step] to which the setting's [value] is rounded.
 * @property visibility A function that determines whether the setting [isVisible].
 * @property description A [description] of the setting.
 */
class ByteSetting(
    override val name: String,
    defaultValue: Byte,
    override val range: ClosedRange<Byte>,
    override val step: Byte = 1,
    visibility: () -> Boolean,
    description: String,
) : NumericSetting<Byte>(
    defaultValue,
    range,
    step,
    visibility,
    description
)