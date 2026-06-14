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

package com.lambda.config.settings.comparable

import com.lambda.brigadier.argument.boolean
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.execute
import com.lambda.brigadier.required
import com.lambda.config.Config
import com.lambda.config.entries.Setting
import com.lambda.config.entries.SettingEntryLayer
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.util.extension.CommandBuilder
import net.minecraft.command.CommandRegistryAccess

class BooleanSetting(
	name: String,
	description: String,
	config: Config,
	layer: SettingEntryLayer<BooleanSetting, Boolean>,
	visibility: () -> Boolean,
	defaultValue: Boolean
) : Setting<Boolean>(name, description, defaultValue, layer, config, visibility) {
	override fun ImGuiBuilder.buildLayout() {
		checkbox(name, ::value)
		lambdaTooltip(description)
	}

	override fun CommandBuilder.buildCommand(registry: CommandRegistryAccess) {
		required(boolean(name)) { parameter ->
			execute {
				trySetValue(parameter().value())
			}
		}
	}
}