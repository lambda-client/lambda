package com.lambda.config.settings

import com.google.gson.reflect.TypeToken
import com.lambda.config.AbstractSetting

/**
 * Represents a [String] setting.
 *
 * @property name The [name] of the setting.
 * @property defaultValue The default [String] [value] of the setting.
 * @property description A [description] of the setting.
 * @property visibility A function that determines whether the setting [isVisible].
 */
class StringSetting(
    override val name: String,
    defaultValue: String,
    description: String,
    visibility: () -> Boolean,
) : AbstractSetting<String>(
    defaultValue,
    TypeToken.get(String::class.java).type,
    description,
    visibility
)
