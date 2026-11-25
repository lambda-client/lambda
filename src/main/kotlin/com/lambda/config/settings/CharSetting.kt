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

package com.lambda.config.settings

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lambda.brigadier.CommandResult.Companion.failure
import com.lambda.brigadier.CommandResult.Companion.success
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.argument.word
import com.lambda.brigadier.executeWithResult
import com.lambda.brigadier.required
import com.lambda.config.Config
import com.lambda.config.entries.ConfigEntryDsl
import com.lambda.config.entries.Setting
import com.lambda.config.entries.SettingEntryLayer
import com.lambda.gui.component.TextInput
import com.lambda.gui.core.observe
import com.lambda.gui.settings.Labeled
import com.lambda.util.extension.CommandBuilder
import net.minecraft.command.CommandRegistryAccess

@ConfigEntryDsl
fun Config.charSetting(
	name: String,
	defaultValue: Char,
	description: String = "",
	visibility: () -> Boolean = { true }
) = setting(name) { layer -> CharSetting(name, description, this, layer, defaultValue, visibility) }

/**
 * @see [com.lambda.config.Config]
 */
class CharSetting(
	name: String,
	description: String,
	config: Config,
	layer: SettingEntryLayer<CharSetting, Char>,
	defaultValue: Char,
	visibility: () -> Boolean
) : Setting<Char>(name, description, defaultValue, layer, config, visibility) {
	@Composable
	override fun Gui() {
		val value by observe { value }
		Labeled(name) {
			TextInput(
				value.toString(),
				{ text -> text.lastOrNull()?.let { this@CharSetting.value = it } },
				Modifier.width(18.dp)
			)
		}
	}

	override fun CommandBuilder.buildCommand(registry: CommandRegistryAccess) {
		required(word(name)) { parameter ->
			executeWithResult {
				val char = parameter().value().firstOrNull() ?: return@executeWithResult failure("Can't parse char type")
				trySetValue(char)
				return@executeWithResult success()
			}
		}
	}
}