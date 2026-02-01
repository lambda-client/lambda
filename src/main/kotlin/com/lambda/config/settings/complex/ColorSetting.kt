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

package com.lambda.config.settings.complex

import com.google.gson.reflect.TypeToken
import com.lambda.brigadier.argument.integer
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.execute
import com.lambda.brigadier.optional
import com.lambda.brigadier.required
import com.lambda.config.Setting
import com.lambda.config.SettingCore
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.util.extension.CommandBuilder
import net.minecraft.command.CommandRegistryAccess
import java.awt.Color

/**
 * @see [com.lambda.config.Configurable]
 */
class ColorSetting(defaultValue: Color) : SettingCore<Color>(
	defaultValue,
	TypeToken.get(Color::class.java).type
) {
	context(setting: Setting<*, Color>)
	override fun ImGuiBuilder.buildLayout() {
		colorEdit(setting.name, ::value)
		lambdaTooltip(setting.description)
	}

	context(setting: Setting<*, Color>)
	override fun CommandBuilder.buildCommand(registry: CommandRegistryAccess) {
		required(integer("Red", 0, 255)) { red ->
			required(integer("Green", 0, 255)) { green ->
				required(integer("Blue", 0, 255)) { blue ->
					optional(integer("Alpha", 0, 255)) { alpha ->
						execute {
							val alphaValue = alpha?.let { it().value() } ?: 255
							setting.trySetValue(Color(red().value(), green().value(), blue().value(), alphaValue))
						}
					}
				}
			}
		}
	}
}
