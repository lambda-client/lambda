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

package com.lambda.gui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lambda.config.Config
import com.lambda.config.automation.UserAutomationConfig
import com.lambda.gui.component.Button
import com.lambda.gui.component.ScrollColumn
import com.lambda.gui.component.SoftTextShadow
import com.lambda.gui.component.Text
import com.lambda.gui.theme.Type
import com.lambda.module.Module

@Composable
internal fun ConfigSettings(
	config: Config,
	maxHeight: Dp,
	onDone: () -> Unit,
	modifier: Modifier = Modifier
) {
	Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
		Text(
			"${config.name} Settings",
			Modifier.fillMaxWidth().padding(horizontal = 4.dp),
			style = Type.title
		)
		if (config is Module) {
			var page by remember(config) { mutableStateOf(PanelPage.Settings) }
			Column(Modifier.heightIn(max = maxHeight)) {
				ModuleSettings(config, page, { page = it }, rememberScrollState())
			}
		} else {
			ScrollColumn(Modifier.heightIn(max = maxHeight)) {
				SoftTextShadow {
					if (config is UserAutomationConfig) SettingRow(config.linkedModules)
					SettingsList(config.settingLayers)
				}
			}
		}
		Row(
			Modifier.fillMaxWidth().padding(horizontal = 4.dp),
			horizontalArrangement = Arrangement.spacedBy(4.dp)
		) {
			if (config is UserAutomationConfig) {
				Button("Delete") {
					config.delete()
					onDone()
				}
			}
			Spacer(Modifier.weight(1f))
			if (config !is Module) Button("Reset") { config.resetSettings() }
			Button("Done", onClick = onDone)
		}
	}
}
