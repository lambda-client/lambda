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
import com.lambda.config.SettingEditorDsl
import com.lambda.config.SettingGroupEditor
import com.lambda.context.SafeContext
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.threading.runSafe
import com.lambda.util.StringUtils.levenshteinDistance
import imgui.ImGuiListClipper
import imgui.flag.ImGuiChildFlags
import imgui.flag.ImGuiSelectableFlags.DontClosePopups
import java.lang.reflect.Type

/**
 * This generic collection settings handles all [Comparable] values (i.e not classes) and serialize
 * their values by calling [Any.toString] and loads them by comparing what's in the [immutableCollection].
 * This behaviour is by design. If you wish to store collections of non-comparable values you must use [ClassCollectionSetting].
 *
 * If you wish to use a different codec or simply display values differently you must create your own
 * collection setting.
 *
 * @see [com.lambda.config.Configurable]
 */
open class CollectionSetting<T : Any>(
    override var name: String,
    defaultValue: MutableCollection<T>,
    private var immutableCollection: Collection<T>,
    type: Type,
    description: String,
    visibility: () -> Boolean,
) : AbstractSetting<MutableCollection<T>>(
    name,
    defaultValue,
    type,
    description,
    visibility
) {
    private var searchFilter = ""
    private val strListType =
        TypeToken.getParameterized(Collection::class.java, String::class.java).type

    private val selectListeners = mutableListOf<SafeContext.(T) -> Unit>()
    private val deselectListeners = mutableListOf<SafeContext.(T) -> Unit>()

    fun onSelect(block: SafeContext.(T) -> Unit) = apply {
        selectListeners.add(block)
    }

    fun onDeselect(block: SafeContext.(T) -> Unit) = apply {
        deselectListeners.add(block)
    }

    override fun ImGuiBuilder.buildLayout() {
        val text = if (value.size == 1) "item" else "items"

        combo("##$name", "$name: ${value.size} $text") {
            inputText("##$name-SearchBox", ::searchFilter)

            child(
                strId = "##$name-ComboOptionsChild",
                childFlags = ImGuiChildFlags.AutoResizeY or ImGuiChildFlags.AlwaysAutoResize,
            ) {
                val list = immutableCollection
                    .filter { searchFilter == "" || searchFilter.levenshteinDistance(it.toString()) < 3 }

                ImGuiListClipper.forEach { // not actually iterating
                    it.begin(list.size)

                    while (it.step()) {
                        for (i in it.displayStart..it.displayEnd) {
                            val v = list.getOrNull(i) ?: continue
                            val selected = value.contains(v)

                            selectable(
                                label = v.toString(),
                                selected = selected,
                                flags = DontClosePopups
                            ) {
                                if (selected) {
                                    value.remove(v)
                                    runSafe { deselectListeners.forEach { f -> f(v) } }
                                } else {
                                    value.add(v)
                                    runSafe { selectListeners.forEach { f -> f(v) } }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun toJson(): JsonElement =
        gson.toJsonTree(value.map { it.toString() })

    override fun loadFromJson(serialized: JsonElement) {
        val strList = gson.fromJson<Collection<String>>(serialized, strListType)
            .mapNotNull { str -> immutableCollection.find { it.toString() == str } }
            .toMutableList()

        value = strList
    }

    companion object {
        @SettingEditorDsl
        @Suppress("unchecked_cast")
        fun <T : Any> SettingGroupEditor.TypedEditBuilder<Collection<T>>.immutableCollection(collection: Collection<T>) {
            (settings as Collection<CollectionSetting<T>>).forEach { it.immutableCollection = collection }
        }
    }
}
