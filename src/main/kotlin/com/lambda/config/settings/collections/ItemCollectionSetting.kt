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

import com.google.gson.reflect.TypeToken
import com.lambda.config.serializer.ItemCodec
import com.lambda.gui.dsl.ImGuiBuilder
import imgui.flag.ImGuiSelectableFlags.DontClosePopups
import net.minecraft.item.Item

class ItemCollectionSetting(
	override var name: String,
	private val immutableCollection: Collection<Item>,
	defaultValue: MutableCollection<Item>,
	description: String,
	visibility: () -> Boolean,
) : CollectionSettings<Item>(
	name,
	immutableCollection,
	defaultValue,
	TypeToken.getParameterized(Collection::class.java, Item::class.java).type,
	description,
	visibility,
) {
	override fun ImGuiBuilder.buildLayout() {
		val text = if (value.size == 1) "item" else "items"

		combo("##$name", "$name: ${value.size} $text") {
			value.toMutableList() // Copy the list instead of iterating the immutable one
				.forEach {
					selectable(
						label = ItemCodec.stringify(it),
						selected = true,
						flags = DontClosePopups
					) { value.remove(it) }
				}
		}

		button("Add") { openPopup("Items") }

		popup("Items") {
			filter("Search for items") { filter ->
				immutableCollection
					.filter {
						//filter.inputBuffer.levenshteinDistance(ItemCodec.stringify(it)) < 3 &&
								!value.contains(it)
					}
					.forEach {
						text(ItemCodec.stringify(it))
						sameLine()
						button("Add") { value.add(it) }
					}
			}
		}
	}
}