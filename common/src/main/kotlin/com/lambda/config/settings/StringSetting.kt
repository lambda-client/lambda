/*
 * Copyright 2024 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

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
