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

@file:Suppress("unused")

package com.lambda.config.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lambda.brigadier.argument.greedyString
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.execute
import com.lambda.brigadier.required
import com.lambda.config.Config
import com.lambda.config.ConfigEditorD5l
import com.lambda.config.SettingEditBuilder
import com.lambda.config.entries.ConfigEntryDsl
import com.lambda.config.entries.Setting
import com.lambda.config.entries.SettingEntryLayer
import com.lambda.gui.component.Centered
import com.lambda.gui.component.Text
import com.lambda.gui.component.TextInput
import com.lambda.gui.core.observe
import com.lambda.gui.settings.ALTERED_GAP
import com.lambda.gui.settings.AlteredDot
import com.lambda.gui.settings.Labeled
import com.lambda.gui.settings.ROW_INSET
import com.lambda.gui.settings.TEXT_FIELD_WIDTH
import com.lambda.util.extension.CommandBuilder
import net.minecraft.command.CommandRegistryAccess

@ConfigEntryDsl
fun Config.stringSetting(
    name: String,
    defaultValue: String,
    multiline: Boolean = false,
    description: String = "",
    visibility: () -> Boolean = { true }
) = setting(name) { layer -> StringSetting(name, description, this, layer, defaultValue, visibility, multiline) }

/**
 * @see [com.lambda.config.Config]
 */
class StringSetting(
    name: String,
    description: String,
    config: Config,
    layer: SettingEntryLayer<StringSetting, String>,
    defaultValue: String,
    visibility: () -> Boolean,
    var multiline: Boolean = false,
) : Setting<String>(name, description, defaultValue, layer, config, visibility) {

	@Composable
	override fun Gui() {
		val value by observe { value }
		if (multiline) Column(
			Modifier.fillMaxWidth(),
			verticalArrangement = Arrangement.spacedBy(1.dp)
		) {
			Row(
				Modifier.padding(start = ROW_INSET, top = 1.dp),
				Arrangement.spacedBy(ALTERED_GAP),
				Centered.VERTICALLY
			) {
				AlteredDot()
				Text(name, maxLines = 1)
			}
			TextInput(
				value,
				{ this@StringSetting.value = it },
				Modifier.fillMaxWidth(),
				singleLine = false
			)
		}
		else Labeled(name) {
			TextInput(
				value,
				{ this@StringSetting.value = it },
				Modifier.width(TEXT_FIELD_WIDTH)
			)
        }
    }

    override fun CommandBuilder.buildCommand(registry: CommandRegistryAccess) {
        required(greedyString(name)) { parameter ->
            execute {
                trySetValue(parameter().value())
            }
        }
    }
}

@Suppress("unchecked_cast")
@ConfigEditorD5l
fun SettingEditBuilder<String>.multiline(multiline: Boolean) {
    (entries as Collection<StringSetting>).forEach { it.multiline = multiline }
}
