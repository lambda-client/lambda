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

package com.lambda.config.settings.complex

import com.lambda.brigadier.argument.blockState
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.execute
import com.lambda.brigadier.required
import com.lambda.config.Config
import com.lambda.config.entries.Setting
import com.lambda.config.entries.SettingEntryLayer
import com.lambda.config.serializers.BlockSerializer
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.imgui.ImGui
import com.lambda.imgui.ImGuiListClipper
import com.lambda.imgui.callback.ImListClipperCallback
import com.lambda.imgui.flag.ImGuiChildFlags
import com.lambda.imgui.flag.ImGuiPopupFlags
import com.lambda.util.extension.CommandBuilder
import net.minecraft.block.Block
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.registry.Registries

class BlockSetting(
	name: String,
	description: String,
	config: Config,
	layer: SettingEntryLayer<BlockSetting, Block>,
	visibility: () -> Boolean,
	defaultValue: Block
) : Setting<Block>(name, description, defaultValue, layer, config, visibility) {
	private var searchFilter = ""

	override fun ImGuiBuilder.buildLayout() {
		val popupId = "##$name-popup"

		button("$name: ${BlockSerializer.stringify(value)}") {
			ImGui.openPopup(popupId)
		}
		lambdaTooltip(description)

		ImGui.setNextWindowSizeConstraints(350f, 0f, Float.MAX_VALUE, io.displaySize.y * 0.5f)
		popupContextItem(popupId, ImGuiPopupFlags.None) {
			inputText("##$name-SearchBox", ::searchFilter)

			val q = searchFilter.trim()
			val filtered = Registries.BLOCK
				.filter { q.isEmpty() || BlockSerializer.stringify(it).contains(q, ignoreCase = true) }

			child(
				strId = "##$name-List",
				width = 700f,
				height = 200f,
				childFlags = ImGuiChildFlags.Border or ImGuiChildFlags.ResizeX or ImGuiChildFlags.ResizeY,
			) {
				val callback = object : ImListClipperCallback() {
					override fun accept(index: Int) {
						val block = filtered.getOrNull(index) ?: return
						selectable(
							label = BlockSerializer.stringify(block),
							selected = block == value
						) {
							value = block
							closeCurrentPopup()
						}
					}
				}
				ImGuiListClipper.forEach(filtered.size, callback)
			}
		}
	}

	override fun CommandBuilder.buildCommand(registry: CommandRegistryAccess) {
		required(blockState(name, registry)) { argument ->
			execute {
				trySetValue(argument().value().blockState.block)
			}
		}
	}
}