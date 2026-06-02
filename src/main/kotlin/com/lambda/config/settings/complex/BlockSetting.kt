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
import com.lambda.config.Config.SettingLayer
import com.lambda.config.Setting
import com.lambda.config.SettingCore
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.util.extension.CommandBuilder
import net.minecraft.block.Block
import net.minecraft.command.CommandRegistryAccess

class BlockSetting(
	name: String,
	description: String,
	config: Config,
	layer: SettingLayer.Single<*, Block>,
	visibility: () -> Boolean,
	defaultValue: Block
) : Setting<Block>(name, description, SettingCore(defaultValue), config, layer, visibility) {
	override fun ImGuiBuilder.buildLayout() {}

	override fun CommandBuilder.buildCommand(registry: CommandRegistryAccess) {
		required(blockState(name, registry)) { argument ->
			execute {
				trySetValue(argument().value().blockState.block)
			}
		}
	}
}