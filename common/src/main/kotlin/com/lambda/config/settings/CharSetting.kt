package com.lambda.config.settings

import com.lambda.config.AbstractSetting

/**
 * Represents a [Char] setting.
 *
 * @property name The [name] of the setting.
 * @property defaultValue The default [Char] [value] of the setting.
 * @property visibility A function that determines whether the setting [isVisible].
 * @property description A [description] of the setting.
 */
class CharSetting(
    override val name: String,
    defaultValue: Char,
    description: String,
    visibility: () -> Boolean,
) : AbstractSetting<Char>(
    defaultValue,
    description,
    visibility
)