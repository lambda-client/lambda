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

package com.lambda.config.settings.collections

import com.google.gson.JsonElement
import com.lambda.Lambda.gson
import com.lambda.config.AbstractSetting
import java.lang.reflect.Type

/**
 * @see [com.lambda.config.Configurable]
 */
class MapSetting<K, V>(
    override val name: String,
    private val defaultValue: MutableMap<K, V>,
    type: Type,
    description: String,
    private val hackDelegates: Boolean,
    visibility: () -> Boolean,
) : AbstractSetting<MutableMap<K, V>>(
    defaultValue,
    type,
    description,
    visibility
) {
    override fun toJson(): JsonElement {
        return if (hackDelegates) gson.toJsonTree(defaultValue, type)
        else super.toJson()
    }

    override fun loadFromJson(serialized: JsonElement) {
        if (hackDelegates) {
            defaultValue.putAll(gson.fromJson(serialized, type))
            setValue(this, ::value, defaultValue)
        }
        else super.loadFromJson(serialized)
    }
}
