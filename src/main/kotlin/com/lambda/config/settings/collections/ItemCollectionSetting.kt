/*
 * Copyright 2026 Lambda
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
import com.lambda.config.Setting
import com.lambda.config.serializer.ItemCodec
import com.lambda.gui.dsl.ImGuiBuilder
import net.minecraft.item.Item

class ItemCollectionSetting(
	immutableCollection: Collection<Item>,
	defaultValue: MutableCollection<Item>
) : CollectionSetting<Item>(
	defaultValue,
	immutableCollection,
	TypeToken.getParameterized(Collection::class.java, Item::class.java).type,
	serialize = true,
) {
	context(setting: Setting<*, MutableCollection<Item>>)
	override fun ImGuiBuilder.buildLayout() = buildComboBox("item") { ItemCodec.stringify(it) }
}