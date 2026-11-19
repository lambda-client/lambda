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

package com.lambda.module.modules.hud

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.lambda.config.settings.complex.Bind
import com.lambda.gui.core.observe
import com.lambda.gui.hud.HudText
import com.lambda.module.HudModule
import com.lambda.module.ModuleRegistry
import com.lambda.module.ModuleTag

@Suppress("unused")
object ModuleList : HudModule(
    name = "ModuleList",
    tag = ModuleTag.HUD,
) {
	val onlyBound by setting("Only Bound", false, "Only displays modules with a keybind")
	val showKeybind by setting("Show Keybind", true, "Display keybind next to a module")

    init {
        drawSetting.value = false
    }

    @Composable
    override fun Content() {
        val entries by observe {
            ModuleRegistry.modules
                .filter { it.isEnabled && it.draw && !it.alwaysEnabled }
                .map { it.name to it.keybind.takeIf { bind -> bind.key != 0 || bind.mouse != -1 } }
                .filter { (_, bind) -> bind != null || !onlyBound }
        }
        val showKeybind by observe { showKeybind }
        Column {
            entries.forEach { (name, bind) ->
                Row {
                    HudText(name)
                    if (showKeybind) HudText(" [${(bind ?: Bind.EMPTY).name}]", color = if (bind != null) Color.Green else Color.Red)
                }
            }
        }
    }
}
