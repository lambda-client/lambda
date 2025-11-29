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
import com.lambda.Lambda.gson
import com.lambda.config.AbstractSetting
import com.lambda.config.AutomationConfig
import com.lambda.config.Stringifiable
import com.lambda.context.SafeContext
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.threading.runSafe
import imgui.flag.ImGuiSelectableFlags.DontClosePopups
import java.lang.reflect.Type

/**
 * @see [com.lambda.config.Configurable]
 */
class CollectionSettings<T : Any>(
    override var name: String,
    private var immutableCollection: Collection<T>,
    defaultValue: MutableCollection<T>,
    type: Type,
    description: String,
    private val serializer: Stringifiable<T>,
    visibility: () -> Boolean,
) : AbstractSetting<MutableCollection<T>>(
    name,
    defaultValue,
    type,
    description,
    visibility
) {
    private val selectListeners = mutableListOf<SafeContext.(T) -> Unit>()
    private val deselectListeners = mutableListOf<SafeContext.(T) -> Unit>()

    override fun ImGuiBuilder.buildLayout() {
        combo("##$name", "$name: ${value.size} item(s)") {
            immutableCollection
                .forEach {
                    val isSelected = value.contains(it)

                    selectable(
                        serializer.stringify(it), isSelected,
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

    override fun toJson(): JsonElement = gson.toJsonTree(value)

    override fun loadFromJson(serialized: JsonElement) {
        value = gson.fromJson<MutableList<T>>(serialized, type)
            .toMutableList()
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
        fun <T : Any> AutomationConfig.TypedEditBuilder<Collection<T>>.immutableCollection(collection: Collection<T>) {
            (settings as Collection<CollectionSettings<T>>).forEach { it.immutableCollection = collection }
        }
    }
}
