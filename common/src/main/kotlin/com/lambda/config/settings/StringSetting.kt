package com.lambda.config.settings

import com.lambda.config.AbstractSetting

/**
 * Represents a [String] setting.
 *
 * @property name The [name] of the setting.
 * @property defaultValue The default [String] [value] of the setting.
 * @property visibility A function that determines whether the setting [isVisible].
 * @property description A [description] of the setting.
 */
class StringSetting(
    override val name: String,
    defaultValue: String,
    visibility: () -> Boolean,
    description: String,
) : AbstractSetting<String>(
    defaultValue,
    visibility,
    description
)