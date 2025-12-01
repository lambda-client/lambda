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
import com.lambda.gui.dsl.ImGuiBuilder
import imgui.flag.ImGuiSelectableFlags.DontClosePopups

/**
 * @see [com.lambda.config.settings.collections.CollectionSettings]
 * @see [com.lambda.config.Configurable]
 */
class ClassCollectionSetting<T : Any>(
	override var name: String,
	private val immutableCollection: Collection<T>,
	defaultValue: MutableCollection<T>,
	description: String,
	visibility: () -> Boolean,
) : CollectionSetting<T>(
	name,
	immutableCollection,
	defaultValue,
	TypeToken.getParameterized(Collection::class.java, Any::class.java).type,
	description,
	visibility,
) {
	override fun ImGuiBuilder.buildLayout() {
		combo("##$name", "$name: ${value.size} item(s)") {
			immutableCollection
				.forEach {
					val isSelected = value.contains(it)

					selectable(
						label = it.className,
						selected = isSelected,
						flags = DontClosePopups,
					) {
						if (isSelected) value.remove(it)
						else value.add(it)
					}
				}
		}
	}

	val Any.className: String get() = this::class.java.name
		.substringAfter("${this::class.java.packageName}.")
		.replace('$', '.')

	// When serializing the list to json we do not want to serialize the elements' classes, but their stringified representation.
	// If we do serialize the classes we'll run into missing type adapters errors by Gson.
	// This is intended behaviour. If you wish your collection settings to display something else then you must extend this class.
	override fun toJson(): JsonElement = gson.toJsonTree(value.map { it.className })

	override fun loadFromJson(serialized: JsonElement) {
		val strList = gson.fromJson<MutableList<String>>(serialized, type)
			.mapNotNull { str -> immutableCollection.find { it.className == str } }
			.toMutableList()

		value = strList
	}
}