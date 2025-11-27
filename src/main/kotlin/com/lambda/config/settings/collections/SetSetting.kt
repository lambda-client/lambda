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
import com.lambda.config.AutomationConfig
import com.lambda.context.SafeContext
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.threading.runSafe
import imgui.flag.ImGuiSelectableFlags.DontClosePopups
import java.lang.reflect.Type

/**
 * @see [com.lambda.config.Configurable]
 */
class SetSetting<T : Any>(
    override var name: String,
    private var immutableSet: Set<T>,
    defaultValue: MutableSet<T>,
    type: Type,
    description: String,
    visibility: () -> Boolean,
) : AbstractSetting<MutableSet<T>>(
    name,
    defaultValue,
    type,
    description,
    visibility
) {
    private val selectListeners = mutableListOf<SafeContext.(T) -> Unit>()
    private val deselectListeners = mutableListOf<SafeContext.(T) -> Unit>()
    private val strSetType =
        TypeToken.getParameterized(Set::class.java, String::class.java).type

    override fun ImGuiBuilder.buildLayout() {
        combo("##$name", "$name: ${value.size} item(s)") {
            immutableSet
                .forEach {
                    val isSelected = value.contains(it)

                    selectable(
                        it.toString(), isSelected,
                        flags = DontClosePopups
                    ) {
                        if (isSelected) {
                            value.remove(it)
                            runSafe { deselectListeners.forEach { listener -> listener(it) } }
                        } else {
                            value.add(it)
                            runSafe { selectListeners.forEach { listener -> listener(it) } }
                        }
                    }
                }
        }
    }

    // When serializing the list to json we do not want to serialize the elements' classes, but
    // their stringified representation.
    // If we do serialize the classes we'll run into missing type adapters errors by Gson.
    override fun toJson(): JsonElement =
        gson.toJsonTree(value.map { it.toString() })

    override fun loadFromJson(serialized: JsonElement) {
        val strSet = gson.fromJson<Set<String>>(serialized, strSetType)
            .mapNotNull { str -> immutableSet.find { it.toString() == str } }
            .toMutableSet()

        value = strSet
    }

    fun onSelect(block: SafeContext.(T) -> Unit) = apply {
        selectListeners.add(block)
    }

    fun onDeselect(block: SafeContext.(T) -> Unit) = apply {
        deselectListeners.add(block)
    }

    companion object {
        @AutomationConfig.SettingEditorDsl
        @Suppress("unchecked_cast")
        fun <T : Any> AutomationConfig.TypedEditBuilder<MutableSet<T>>.immutableSet(immutableSet: Set<T>) {
            (settings as Collection<SetSetting<T>>).forEach { it.immutableSet = immutableSet }
        }
    }
}
