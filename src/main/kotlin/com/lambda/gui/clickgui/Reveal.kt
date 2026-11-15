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

package com.lambda.gui.clickgui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lambda.config.Config
import com.lambda.config.automation.AutomationConfig
import com.lambda.config.entries.Setting
import com.lambda.gui.GuiConfig
import com.lambda.gui.LambdaGui
import com.lambda.module.HudModule
import com.lambda.module.Module
import com.lambda.module.ModuleRegistry
import com.lambda.module.ModuleTag

/**
 * What a search result points at, shown where it lives instead of in the list of results.
 *
 * A module's category is brought back if it was hidden or folded away, and the module is lit
 * for a moment. A setting has the panel or window it is in opened on the right tab, with the
 * groups around it unfolded, and is lit there. Whatever draws a module or a setting looks here
 * to see whether it is the one.
 */
internal object Reveal {
    var module: Module? by mutableStateOf(null)
        private set
    var setting: Setting<*>? by mutableStateOf(null)
        private set

    /** How long what was revealed stays lit */
    const val MILLIS = 1800L

    fun show(module: Module) = show(module, null)

    /** Shows [setting] of [config], or [config] itself when it is a module and no setting is given */
    fun show(config: Config, setting: Setting<*>?) {
        val owner = owner(config)
        module = owner
        this.setting = setting
        when (owner) {
            // HUD elements live in the HUD editor, which opens the element's settings for a setting
            is HudModule -> LambdaGui.open(LambdaGui.Screen.HudEditor)
            // ...modules in the click GUI, which opens the module's settings for a setting
            is Module -> uncover(owner)
            // The rest only have the window their menu entry opens
            else -> ClickGuiState.dialog = ClickGuiDialog.Settings(config)
        }
    }

    /**
     * The module whose settings show [config]: the module itself, or the one whose own automation
     * config it is, which is listed in its settings under "Automation Config"
     */
    private fun owner(config: Config): Module? =
        config as? Module ?: config.takeIf { it !== AutomationConfig.DEFAULT }
            ?.let { ModuleRegistry.modules.firstOrNull { module -> module.automationConfig === config } }

    fun clear() {
        module = null
        setting = null
    }

    /** Makes sure the row of [module] is on screen to be pointed at */
    private fun uncover(module: Module) {
        val tag = module.tag
        if (tag == ModuleTag.DEBUG) ClickGuiState.developerMode = true else ModuleTag.shownTags += tag
        module.showInClickGui.value = true
        GuiConfig.windows[tag.name]?.takeIf { it.collapsed }?.let {
            GuiConfig.windows += tag.name to it.copy(collapsed = false)
        }
    }
}
