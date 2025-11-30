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

import com.lambda.config.serializer.BlockCodec
import com.lambda.gui.dsl.ImGuiBuilder
import imgui.flag.ImGuiSelectableFlags.DontClosePopups
import net.minecraft.block.Block

class BlockCollectionSetting(
	override var name: String,
	private val immutableCollection: Collection<Block>,
	defaultValue: MutableCollection<Block>,
	description: String,
	visibility: () -> Boolean,
) : CollectionSettings<Block>(
	name,
	immutableCollection,
	defaultValue,
	Block::class.java,
	description,
	visibility,
) {
	override fun ImGuiBuilder.buildLayout() {
		val text = if (value.size == 1) "block" else "blocks"

		combo("##$name", "$name: ${value.size} $text") {
			value.toMutableList() // Copy the list instead of iterating the immutable one
				.forEach {
					selectable(
						label = BlockCodec.stringify(it),
						selected = true,
						flags = DontClosePopups
					) { value.remove(it) }
				}
		}

		button("Add") { openPopup("Blocks") }

		popup("Blocks") {
			filter("Search for blocks") { filter ->
				immutableCollection
					.filter {
						//filter.inputBuffer.levenshteinDistance(ItemCodec.stringify(it)) < 3 &&
						!value.contains(it)
					}
					.forEach {
						text(BlockCodec.stringify(it))
						sameLine()
						button("Add") { value.add(it) }
					}
			}
		}
	}
}