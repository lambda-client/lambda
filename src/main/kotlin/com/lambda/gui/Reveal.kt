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

package com.lambda.gui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lambda.config.Config
import com.lambda.config.automation.AutomationConfig
import com.lambda.config.entries.Setting
import com.lambda.gui.clickgui.ClickGuiDialog
import com.lambda.gui.clickgui.ClickGuiState
import com.lambda.gui.core.awaitFrames
import com.lambda.module.HudModule
import com.lambda.module.Module
import com.lambda.module.ModuleRegistry
import com.lambda.module.ModuleTag

internal object Reveal {
	private const val MILLIS = 1800L

	var module: Module? by mutableStateOf(null)
		private set
	var setting: Setting<*>? by mutableStateOf(null)
		private set

	fun show(config: Config, setting: Setting<*>? = null) {
		val owner = owner(config)
		module = owner
		this.setting = setting
		when (owner) {
			is HudModule -> LambdaGui.open(LambdaGui.Screen.HudEditor)
			is Module -> uncover(owner)
			else -> ClickGuiState.dialog = ClickGuiDialog.Settings(config)
		}
	}

	@Composable
	fun Expiry() {
		val module = module
		val setting = setting
		LaunchedEffect(module, setting) {
			if (module == null && setting == null) return@LaunchedEffect
			awaitFrames(MILLIS)
			this@Reveal.module = null
			this@Reveal.setting = null
		}
	}

	private fun owner(config: Config): Module? = config as? Module
		?: config.takeIf { it !== AutomationConfig.DEFAULT }
			?.let {
				ModuleRegistry.modules.firstOrNull { module ->
					module.automationConfig === config
				}
			}

	private fun uncover(module: Module) {
		val tag = module.tag
		if (tag == ModuleTag.DEBUG) ClickGuiState.developerMode =
			true else ModuleTag.shownTags += tag
		module.showInClickGui.value = true
		GuiConfig.windows[tag.name]?.takeIf { it.collapsed }?.let {
			GuiConfig.windows += tag.name to it.copy(collapsed = false)
		}
	}
}
