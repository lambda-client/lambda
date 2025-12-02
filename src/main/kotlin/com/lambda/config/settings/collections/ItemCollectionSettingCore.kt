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
import com.lambda.config.serializer.ItemCodec
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.util.StringUtils.levenshteinDistance
import imgui.ImGuiListClipper
import imgui.flag.ImGuiChildFlags
import imgui.flag.ImGuiSelectableFlags.DontClosePopups
import net.minecraft.item.Item

class ItemCollectionSettingCore(
	private val immutableCollection: Collection<Item>,
	defaultValue: MutableCollection<Item>
) : CollectionSettingCore<Item>(
	defaultValue,
	immutableCollection,
	TypeToken.getParameterized(Collection::class.java, Item::class.java).type
) {
	private var searchFilter = ""

	context(setting: Setting<*, MutableCollection<Item>>)
	override fun ImGuiBuilder.buildLayout() {
		val text = if (value.size == 1) "item" else "items"

		combo("##${setting.name}", "${setting.name}: ${value.size} $text") {
			inputText("##${setting.name}-SearchBox", ::searchFilter)

			child(
				strId = "##${setting.name}-ComboOptionsChild",
				childFlags = ImGuiChildFlags.AutoResizeY or ImGuiChildFlags.AlwaysAutoResize,
			) {
				val list = immutableCollection
					.filter { searchFilter == "" || searchFilter.levenshteinDistance(ItemCodec.stringify(it)) < 3 }

				ImGuiListClipper.forEach { // not actually iterating
					it.begin(list.size)

					while (it.step()) {
						for (i in it.displayStart..it.displayEnd) {
							val v = list.getOrNull(i) ?: continue
							val selected = value.contains(v)

							selectable(
								label = ItemCodec.stringify(v),
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

	context(setting: Setting<*, MutableCollection<Item>>)
	override fun toJson(): JsonElement = gson.toJsonTree(value, type)

	context(setting: Setting<*, MutableCollection<Item>>)
	override fun loadFromJson(serialized: JsonElement) {
		value = gson.fromJson<Collection<Item>>(serialized, type)
			.toMutableList()
	}
}