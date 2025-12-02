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
import com.lambda.config.Setting
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.util.StringUtils.levenshteinDistance
import com.lambda.util.reflections.className
import imgui.ImGuiListClipper
import imgui.flag.ImGuiChildFlags
import imgui.flag.ImGuiSelectableFlags.DontClosePopups

/**
 * @see [com.lambda.config.settings.collections.CollectionSettingCore]
 * @see [com.lambda.config.Configurable]
 */
class ClassCollectionSettingCore<T : Any>(
	private val immutableCollection: Collection<T>,
	defaultValue: MutableCollection<T>
) : CollectionSettingCore<T>(
	defaultValue,
	immutableCollection,
	TypeToken.getParameterized(Collection::class.java, Any::class.java).type
) {
	private var searchFilter = ""

	context(setting: Setting<*, MutableCollection<T>>)
	override fun ImGuiBuilder.buildLayout() {
		val text = if (value.size == 1) "item" else "items"

		combo("##${setting.name}", "${setting.name}: ${value.size} $text") {
			inputText("##${setting.name}-SearchBox", ::searchFilter)

			child(
				strId = "##${setting.name}-ComboOptionsChild",
				childFlags = ImGuiChildFlags.AutoResizeY or ImGuiChildFlags.AlwaysAutoResize,
			) {
				val list = immutableCollection
					.filter { searchFilter == "" || searchFilter.levenshteinDistance(it.className) < 3 }

				ImGuiListClipper.forEach { // not actually iterating
					it.begin(list.size)

					while (it.step()) {
						for (i in it.displayStart..it.displayEnd) {
							val v = list.getOrNull(i) ?: continue
							val selected = value.contains(v)

							selectable(
								label = v.className,
								selected = selected,
								flags = DontClosePopups
							) {
								if (selected) value.remove(v)
								else value.add(v)
							}
						}
					}
				}
			}
		}
	}

	// When serializing the list to json we do not want to serialize the elements' classes, but their stringified representation.
	// If we do serialize the classes we'll run into missing type adapters errors by Gson.
	// This is intended behaviour. If you wish your collection settings to display something else then you must extend this class.
	context(setting: Setting<*, MutableCollection<T>>)
	override fun toJson(): JsonElement = gson.toJsonTree(value.map { it.className })

	context(setting: Setting<*, MutableCollection<T>>)
	override fun loadFromJson(serialized: JsonElement) {
		val strList = gson.fromJson<MutableList<String>>(serialized, type)
			.mapNotNull { str -> immutableCollection.find { it.className == str } }
			.toMutableList()

		value = strList
	}
}