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
import net.minecraft.item.Item

class ItemCollectionSetting(
	immutableCollection: Collection<Item>,
	defaultValue: MutableCollection<Item>
) : CollectionSetting<Item>(
	defaultValue,
	immutableCollection,
	TypeToken.getParameterized(Collection::class.java, Item::class.java).type
) {
	context(setting: Setting<*, MutableCollection<Item>>)
	override fun ImGuiBuilder.buildLayout() = buildComboBox("item")

	context(setting: Setting<*, MutableCollection<Item>>)
	override fun toJson(): JsonElement = gson.toJsonTree(value, type)

	context(setting: Setting<*, MutableCollection<Item>>)
	override fun loadFromJson(serialized: JsonElement) {
		value = gson.fromJson<Collection<Item>>(serialized, type)
			.toMutableList()
	}
}