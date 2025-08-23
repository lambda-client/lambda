/*
 * Copyright 2025 Lambda
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
import com.google.gson.reflect.TypeToken
import com.lambda.Lambda.gson
import com.lambda.config.AbstractSetting
import com.lambda.gui.dsl.ImGuiBuilder
import imgui.flag.ImGuiSelectableFlags.DontClosePopups
import java.lang.reflect.Type

/**
 * @see [com.lambda.config.Configurable]
 */
class ListSetting<T : Any>(
    override val name: String,
    private val immutableList: List<T>,
    defaultValue: MutableList<T>,
    type: Type,
    description: String,
    visibility: () -> Boolean,
) : AbstractSetting<MutableList<T>>(
    defaultValue,
    type,
    description,
    visibility
) {
    private val strListType =
        TypeToken.getParameterized(MutableList::class.java, String::class.java).type

    override fun ImGuiBuilder.buildLayout() {
        combo("##$name", "$name: ${value.size} item(s)") {
            immutableList
                .forEach {
                    val isSelected = value.contains(it)

                    selectable(
                        it.toString(), isSelected,
                        flags = DontClosePopups
                    ) { if (isSelected) value.remove(it) else value.add(it) }
                }
        }
    }

    // When serializing the list to json we do not want to serialize the elements' classes, but
    // their stringified representation.
    // If we do serialize the classes we'll run into missing type adapters errors by Gson.
    override fun toJson(): JsonElement =
        gson.toJsonTree(value.map { it.toString() })

    override fun loadFromJson(serialized: JsonElement) {
        val strList = gson.fromJson<MutableList<String>>(serialized, strListType)
            .mapNotNull { str -> immutableList.find { it.toString() == str } }
            .toMutableList()

        value = strList
    }
}
