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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lambda.config.Config
import com.lambda.config.entries.ConfigEntryDsl
import com.lambda.config.entries.Setting
import com.lambda.config.entries.SettingEntryLayer
import com.lambda.gui.component.Button
import com.lambda.gui.theme.Look

@ConfigEntryDsl
fun <T : () -> R, R> Config.functionSetting(
	name: String,
	defaultValue: T,
	description: String = "",
	visibility: () -> Boolean = { true }
) = setting(name) { layer -> FunctionSetting(name, description, defaultValue, this, layer, visibility) }

class FunctionSetting<T : () -> R, R>(
	name: String,
	description: String,
	defaultValue: T,
	config: Config,
	layer: SettingEntryLayer<FunctionSetting<T, R>, T>,
	visibility: () -> Boolean
) : Setting<T>(name, description, defaultValue, layer, config, visibility) {
	@Composable
	override fun Gui() = Button(name, Modifier.fillMaxWidth().height(Look.rowHeight)) { value() }
}
